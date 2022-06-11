@file:UseSerializers(InstantSerializer::class)

package gregc.gregchess.match

import gregc.gregchess.*
import gregc.gregchess.board.Chessboard
import gregc.gregchess.move.Move
import gregc.gregchess.move.MoveEnvironment
import gregc.gregchess.move.connector.*
import gregc.gregchess.piece.*
import gregc.gregchess.player.ChessPlayer
import gregc.gregchess.player.ChessSide
import gregc.gregchess.results.*
import gregc.gregchess.variant.ChessVariant
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@Serializable
class ChessMatch private constructor(
    @Contextual val environment: ChessEnvironment,
    val variant: ChessVariant,
    val components: List<Component>, // TODO: serialize as a map instead
    @SerialName("players") val playerData: ByColor<ChessPlayer>,
    @Contextual val uuid: UUID,
    @SerialName("state") private var state_: State,
    @SerialName("startTime") private var startTime_: Instant?, // TODO: add a way to track true match length and true length of each turn
    @SerialName("endTime") private var endTime_: Instant?,
    @SerialName("results") private var results_: MatchResults?,
    val extraInfo: ExtraInfo,
    var currentTurn: Color
) {
    constructor(environment: ChessEnvironment, variant: ChessVariant, components: Collection<Component>, playerInfo: ByColor<ChessPlayer>, extraInfo: ExtraInfo = ExtraInfo())
            : this(environment, variant, components.toList(), playerInfo, UUID.randomUUID(), State.INITIAL, null, null, null, extraInfo, components.filterIsInstance<Chessboard>().firstOrNull()?.initialFEN?.currentTurn ?: Color.WHITE)

    @Serializable
    data class ExtraInfo(val round: Int = 1, val eventName: String = "Casual match")

    override fun toString() = "ChessMatch(uuid=$uuid)"

    init {
        require((state >= State.RUNNING) == (startTime != null)) { "Start time bad" }
        require((state >= State.STOPPED) == (endTime != null)) { "End time bad" }
        require((state >= State.STOPPED) == (results != null)) { "Results bad" }
        try {
            require(ComponentType.CHESSBOARD)
            for (t in variant.requiredComponents) {
                components.firstOrNull { it.type == t } ?: throw ComponentNotFoundException(t)
            }
            components.forEach { it.init(this) }
        } catch (e: Exception) {
            panic(e)
        }
    }

    val coroutineScope by lazy {
        CoroutineScope(
            environment.coroutineDispatcher +
            SupervisorJob() +
            CoroutineName("Match $uuid") +
            CoroutineExceptionHandler { _, e ->
                e.printStackTrace()
            }
        )
    }

    val board get() = require(ComponentType.CHESSBOARD)

    @Suppress("UNCHECKED_CAST")
    operator fun <T : Component> get(type: ComponentType<T>): T? = components.firstOrNull { it.type == type } as T?

    fun <T : Component> require(type: ComponentType<T>): T = get(type) ?: throw ComponentNotFoundException(type)

    @Transient
    @Suppress("UNCHECKED_CAST")
    val sides: ByColor<ChessSide<*>> = byColor { playerData[it].initSide(it, this@ChessMatch) }

    private fun requireState(s: State) = check(state == s) { "Expected state $s, got state $state!" }

    fun callEvent(e: ChessEvent) = with(MultiExceptionContext()) {
        components.forEach {
            exec {
                it.handleEvent(e)
            }
        }
        rethrow { ChessEventException(e, it) }
    }

    val currentSide: ChessSide<*> get() = sides[currentTurn]

    val currentOpponent: ChessSide<*> get() = sides[!currentTurn]

    private fun Instant.zoned() = atZone(environment.clock.zone)

    var startTime: Instant?
        get() = startTime_
        private set(v) {
            check(state == State.RUNNING) { "Start time set when not running: $state" }
            check(startTime_ == null) {
                val formatter = DateTimeFormatter.ofPattern("uuuu.MM.dd HH:mm:ss z")
                "Start time already set: ${formatter.format(startTime_?.zoned())}, ${formatter.format(v?.zoned())}"
            }
            startTime_ = v
        }

    val zonedStartTime: ZonedDateTime? get() = startTime?.zoned()

    var endTime: Instant?
        get() = endTime_
        private set(v) {
            check(state == State.STOPPED) { "End time set when not stopped: $state" }
            check(endTime_ == null) {
                val formatter = DateTimeFormatter.ofPattern("uuuu.MM.dd HH:mm:ss z")
                "End time already set: ${formatter.format(endTime_?.zoned())}, ${formatter.format(v?.zoned())}"
            }
            endTime_ = v
        }

    val zonedEndTime: ZonedDateTime? get() = endTime?.zoned()

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
            currentTurn++
            startTurn()
        }
    }

    fun previousTurn() {
        requireState(State.RUNNING)
        callEvent(TurnEvent.UNDO)
        currentTurn++
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
        startTime = Instant.now(environment.clock)
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
        currentSide.startTurn()
    }

    private fun startPreviousTurn() {
        requireState(State.RUNNING)
        callEvent(TurnEvent.START)
        currentSide.startTurn()
    }

    var results: MatchResults?
        get() = results_
        private set(v) {
            check(state >= State.STOPPED) { "Results set when not stopped: $state" }
            check(results_ == null) { "Results already set: $results_, $v" }
            results_ = v
        }

    private fun joinAllAndThen(callback: () -> Unit) = coroutineScope.launch {
        coroutineScope.coroutineContext.job.children.filter { it != coroutineContext.job }.toList().joinAll()
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
        endTime = Instant.now(environment.clock)
        sides.forEach(ChessSide<*>::stop)
        callEvent(ChessBaseEvent.STOP)
        joinAllAndThen {
            coroutineScope.cancel()
            sides.forEach(ChessSide<*>::clear)
            callEvent(ChessBaseEvent.CLEAR)
        }
    }

    private fun panic(e: Exception): Nothing {
        state = State.ERROR
        results = drawBy(EndReason.ERROR, e.toString())
        try {
            sides.forEach(ChessSide<*>::stop)
        } catch (ex: Exception) {
            e.addSuppressed(ex)
        }
        joinAllAndThen {
            coroutineScope.cancel()
            sides.forEach(ChessSide<*>::clear)
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

    operator fun get(color: Color): ChessSide<*> = sides[color]

    @Suppress("UNCHECKED_CAST")
    fun <P : Any> getByValueAny(playerValue: P): ChessSide<P>? = sides.toList().firstOrNull { it.player.value == playerValue } as ChessSide<P>?

    inline operator fun <P : Any, reified S : ChessSide<P>> get(playerValue: P): S? = getByValueAny(playerValue) as? S

    @Transient
    private val connectors = mutableMapOf<MoveConnectorType<*>, MoveConnector>()

    private inner class MatchMoveEnvironment : MoveEnvironment {
        override fun updateMoves() = this@ChessMatch.board.updateMoves() // TODO: make this into an event?
        override fun callEvent(e: ChessEvent) = this@ChessMatch.callEvent(e)
        override val variant: ChessVariant get() = this@ChessMatch.variant
        @Suppress("UNCHECKED_CAST")
        override fun <T : MoveConnector> get(type: MoveConnectorType<T>): T = connectors[type] as T
        override val holders: Map<PlacedPieceType<*>, PieceHolder<*>> get() = buildMap {
            for (c in connectors.values)
                for ((t,h) in c.holders)
                    put(t,h)
        }
    }

    private class FakeMoveEnvironment(override val variant: ChessVariant, val connectors: Map<MoveConnectorType<*>, MoveConnector>) : MoveEnvironment {
        override fun updateMoves() = board.updateMoves(variant)
        override fun callEvent(e: ChessEvent) {}
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
        val env = FakeMoveEnvironment(variant, connectors)
        for (m in move) {
            exec {
                m.execute(env)
                m.undo(env)
            }
        }
        rethrow()
    }

}