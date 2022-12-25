package gregc.gregchess.match

import gregc.gregchess.Color
import gregc.gregchess.component.*
import gregc.gregchess.event.*
import gregc.gregchess.move.Move
import gregc.gregchess.move.MoveEnvironment
import gregc.gregchess.move.connector.*
import gregc.gregchess.piece.PlacedPieceType
import gregc.gregchess.results.*
import gregc.gregchess.utils.*
import gregc.gregchess.variant.ChessVariant
import kotlinx.coroutines.*
import kotlinx.serialization.*

@Serializable
class ChessMatch private constructor(
    @Contextual val environment: ChessEnvironment,
    @Contextual val info: MatchInfo,
    val variant: ChessVariant,
    val components: ComponentList,
    @SerialName("state") private var state_: State,
    @SerialName("results") private var results_: MatchResults?,
    val variantOptions: Long
) : ChessEventCaller {
    constructor(environment: ChessEnvironment, info: MatchInfo, variant: ChessVariant, components: Collection<Component>, variantOptions: Long)
            : this(environment, info, variant, ComponentList(components), State.INITIAL, null, variantOptions)

    override fun toString() = "ChessMatch(${info.matchToString()})"

    @Transient
    private val eventManager = ChessEventManager()

    @Transient
    val componentsFacade = ComponentListFacade(this, components)

    override fun callEvent(event: ChessEvent) = eventManager.callEvent(event)

    init {
        require((state >= State.STOPPED) == (results != null)) { "Results bad" }
        for (c in environment.impliedComponents) {
            require(c !in components)
        }
        components.require(ComponentType.CHESSBOARD)
        components.require(ComponentType.SIDES)
        components.require(ComponentType.TIME)
        for (t in variant.requiredComponents + environment.requiredComponents) {
            components.require(t)
        }
        with(MultiExceptionContext()) {
            for (c in components + environment.impliedComponents) {
                exec {
                    c.init(this@ChessMatch, eventManager.registry(c.type))
                }
            }
            catch {
                panic(it)
            }
        }
    }

    val coroutineScope by lazy {
        CoroutineScope(
            environment.coroutineDispatcher +
            SupervisorJob() +
            CoroutineName("Match ${info.matchCoroutineName()}") +
            CoroutineExceptionHandler { _, e ->
                e.printStackTrace()
            }
        )
    }

    val board get() = components.require(ComponentType.CHESSBOARD).getFacade(this)

    val sides get() = components.require(ComponentType.SIDES).getFacade(this)

    val time get() = components.require(ComponentType.TIME).getFacade(this)

    private fun requireState(s: State) = check(state == s) { "Expected state $s, got state $state!" }

    val currentColor: Color get() = board.currentColor

    enum class State {
        INITIAL, RUNNING, STOPPED, ERROR
    }

    var state: State
        get() = state_
        private set(v) {
            check(v > state_) { "Changed state backwards: from $state_ to $v" }
            state_ = v
        }

    val running get() = state == State.RUNNING

    fun nextTurn() {
        requireState(State.RUNNING)
        variant.checkForMatchEnd(this)
        if (running) {
            callEvent(TurnEvent.END)
            board.currentColor++
            startTurn()
        }
    }

    fun previousTurn() {
        requireState(State.RUNNING)
        callEvent(TurnEvent.UNDO)
        board.currentColor++
        startPreviousTurn()
    }

    fun sync() = apply {
        callEvent(ChessBaseEvent.SYNC)
        callEvent(AddMoveConnectorsEvent(connectors))
    }

    fun start() = apply {
        requireState(State.INITIAL)
        callEvent(ChessBaseEvent.START)
        callEvent(AddMoveConnectorsEvent(connectors))
        state = State.RUNNING
        callEvent(ChessBaseEvent.RUNNING)
        startTurn()
    }

    fun update() = try {
        requireState(State.RUNNING)
        callEvent(ChessBaseEvent.UPDATE)
    } catch (e: Exception) {
        panic(e)
    }

    private fun startTurn() {
        requireState(State.RUNNING)
        callEvent(TurnEvent.START)
    }

    private fun startPreviousTurn() {
        requireState(State.RUNNING)
        callEvent(TurnEvent.START)
    }

    var results: MatchResults?
        get() = results_
        private set(v) {
            check(state >= State.STOPPED) { "Results set when not stopped: $state" }
            check(results_ == null) { "Results already set: $results_, $v" }
            results_ = v
        }

    private fun joinAllAndThen(callback: () -> Unit) = coroutineScope.launch {
        coroutineScope.coroutineContext.job.children.filter { it != coroutineContext.job && it.isActive }.toList().joinAll()
    }.invokeOnCompletion {
        callback()
        if (it != null) {
            it.printStackTrace()
            throw it
        }
    }

    fun stop(results: MatchResults) {
        requireState(State.RUNNING)
        state = State.STOPPED
        this.results = results
        callEvent(ChessBaseEvent.STOP)
        joinAllAndThen {
            coroutineScope.cancel()
            callEvent(ChessBaseEvent.CLEAR)
        }
    }

    private fun panic(e: Exception): Nothing {
        state = State.ERROR
        results = drawBy(EndReason.ERROR, e.toString())
        try {
            joinAllAndThen {
                coroutineScope.cancel()
            }
        } catch (ex: Exception) {
            e.addSuppressed(ex)
        }
        try {
            callEvent(ChessBaseEvent.PANIC)
        } catch (ex: Exception) {
            e.addSuppressed(ex)
        }
        throw e
    }

    fun <E> tryOrStopNull(expr: E?): E = try {
        expr!!
    } catch (e: NullPointerException) {
        panic(e)
    }

    @Transient
    private val connectors = mutableMapOf<MoveConnectorType<*>, MoveConnector>()

    private inner class MatchMoveEnvironment : MoveEnvironment {
        override fun callEvent(event: ChessEvent) = this@ChessMatch.callEvent(event)
        override val variant: ChessVariant get() = this@ChessMatch.variant
        override val variantOptions: Long get() = this@ChessMatch.variantOptions
        @Suppress("UNCHECKED_CAST")
        override fun <T : MoveConnector> get(type: MoveConnectorType<T>): T = connectors[type] as T
        override val holders: Map<PlacedPieceType<*>, PieceHolder<*>> get() = buildMap {
            for (c in connectors.values)
                for ((t,h) in c.holders)
                    put(t,h)
        }
    }

    private class FakeMoveEnvironment(override val variant: ChessVariant, override val variantOptions: Long, val connectors: Map<MoveConnectorType<*>, MoveConnector>) : MoveEnvironment {
        override fun callEvent(event: ChessEvent) {}
        @Suppress("UNCHECKED_CAST")
        override fun <T : MoveConnector> get(type: MoveConnectorType<T>): T = connectors[type] as T
        override val holders: Map<PlacedPieceType<*>, PieceHolder<*>> get() = buildMap {
            for (c in connectors.values)
                for ((t,h) in c.holders)
                    put(t,h)
        }
    }

    fun finishMove(move: Move) {
        requireState(State.RUNNING)
        move.execute(MatchMoveEnvironment())
        board.lastMove = move
        if (!move.isPhantomMove)
            nextTurn()
        else
            variant.checkForMatchEnd(this)
    }

    fun undoMove(move: Move) {
        requireState(State.RUNNING)
        move.undo(MatchMoveEnvironment())
    }

    fun resolveName(vararg move: Move) = with(MultiExceptionContext()) {
        val connectors = mutableMapOf<MoveConnectorType<*>, MoveConnector>()
        callEvent(AddFakeMoveConnectorsEvent(connectors))
        val env = FakeMoveEnvironment(variant, variantOptions, connectors)
        for (m in move) {
            exec {
                m.execute(env)
                m.undo(env)
            }
        }
        rethrow()
    }

}