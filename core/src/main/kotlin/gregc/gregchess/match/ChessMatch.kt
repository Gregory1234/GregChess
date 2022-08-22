@file:UseSerializers(InstantSerializer::class, DurationSerializer::class)

package gregc.gregchess.match

import gregc.gregchess.*
import gregc.gregchess.move.Move
import gregc.gregchess.move.MoveEnvironment
import gregc.gregchess.move.connector.*
import gregc.gregchess.piece.PlacedPieceType
import gregc.gregchess.player.ChessSide
import gregc.gregchess.player.ChessSideFacade
import gregc.gregchess.results.*
import gregc.gregchess.variant.ChessVariant
import kotlinx.coroutines.*
import kotlinx.serialization.*
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration

@Serializable
class ChessMatch private constructor(
    @Contextual val environment: ChessEnvironment,
    val variant: ChessVariant,
    val components: List<Component>, // TODO: serialize as a map instead
    val sides: ByColor<ChessSide>,
    @SerialName("state") private var state_: State,
    @SerialName("startTime") private var startTime_: Instant?,
    @SerialName("endTime") private var endTime_: Instant?,
    @SerialName("duration") private var durationCounted: Duration,
    @SerialName("results") private var results_: MatchResults?,
    val extraInfo: ExtraInfo, // TODO: move to ChessEnvironment
    val variantOptions: Long
) : ChessEventCaller {
    constructor(environment: ChessEnvironment, variant: ChessVariant, components: Collection<Component>, sides: ByColor<ChessSide>, variantOptions: Long, extraInfo: ExtraInfo = ExtraInfo())
            : this(environment, variant, components.toList(), sides, State.INITIAL, null, null, Duration.ZERO, null, extraInfo, variantOptions)

    @Serializable
    data class ExtraInfo(val round: Int = 1, val eventName: String = "Casual match")

    override fun toString() = "ChessMatch(${environment.matchToString()})"

    @Transient
    private val eventManager = ChessEventManager()

    override fun callEvent(event: ChessEvent) = eventManager.callEvent(event)

    init {
        require((state >= State.RUNNING) == (startTime != null)) { "Start time bad" }
        require((state >= State.STOPPED) == (endTime != null)) { "End time bad" }
        require((state >= State.STOPPED) == (results != null)) { "Results bad" }
        require(sides.toIndexedList().all { it.second.color == it.first }) { "Sides bad" }
        try {
            require(ComponentType.CHESSBOARD)
            for (t in variant.requiredComponents) {
                components.firstOrNull { it.type == t } ?: throw ComponentNotFoundException(t)
            }
            components.forEach { it.init(this, eventManager) }
            sides.forEach { it.init(this, eventManager) }
        } catch (e: Exception) {
            panic(e)
        }
    }

    val coroutineScope by lazy {
        CoroutineScope(
            environment.coroutineDispatcher +
            SupervisorJob() +
            CoroutineName("Match ${environment.matchCoroutineName()}") +
            CoroutineExceptionHandler { _, e ->
                e.printStackTrace()
            }
        )
    }

    @Transient
    private val facadeCache = mutableMapOf<Component, ComponentFacade<*>>()

    @Suppress("UNCHECKED_CAST")
    fun <T : Component, F : ComponentFacade<T>> makeCachedFacade(mk: (ChessMatch, T) -> F, component: T): F = facadeCache.getOrPut(component) { mk(this, component) } as F

    val board get() = require(ComponentType.CHESSBOARD).getFacade(this)

    @Suppress("UNCHECKED_CAST")
    operator fun <T : Component> get(type: ComponentType<T>): T? = components.firstOrNull { it.type == type } as T?

    fun <T : Component> require(type: ComponentType<T>): T = get(type) ?: throw ComponentNotFoundException(type)

    private fun requireState(s: State) = check(state == s) { "Expected state $s, got state $state!" }

    @Transient
    val sideFacades = byColor { sides[it].createFacade(this) }

    val currentSide: ChessSideFacade<*> get() = sideFacades[board.currentTurn]

    val currentOpponent: ChessSideFacade<*> get() = sideFacades[!board.currentTurn]

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

    @Transient
    private var durationTimeStart: Instant = environment.clock.instant()

    val duration: Duration get() = durationCounted + Duration.between(durationTimeStart, environment.clock.instant())

    private fun updateDuration() {
        val now = environment.clock.instant()
        durationCounted += Duration.between(durationTimeStart, now)
        durationTimeStart = now
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
            board.component.currentTurn++
            startTurn()
        }
    }

    fun previousTurn() {
        requireState(State.RUNNING)
        callEvent(TurnEvent.UNDO)
        board.component.currentTurn++
        startPreviousTurn()
    }

    fun sync() = apply {
        callEvent(ChessBaseEvent.SYNC)
        callEvent(AddMoveConnectorsEvent(connectors))
    }

    fun start() = apply {
        requireState(State.INITIAL)
        callEvent(ChessBaseEvent.START)
        durationTimeStart = environment.clock.instant()
        callEvent(AddMoveConnectorsEvent(connectors))
        state = State.RUNNING
        startTime = Instant.now(environment.clock)
        callEvent(ChessBaseEvent.RUNNING)
        startTurn()
    }

    fun update() = try {
        requireState(State.RUNNING)
        updateDuration()
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
        callEvent(ChessBaseEvent.STOP)
        joinAllAndThen {
            coroutineScope.cancel()
            callEvent(ChessBaseEvent.CLEAR)
        }
    }

    private fun panic(e: Exception): Nothing {
        state = State.ERROR
        results = drawBy(EndReason.ERROR, e.toString())
        joinAllAndThen {
            coroutineScope.cancel()
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

    operator fun get(color: Color): ChessSideFacade<*> = sideFacades[color]

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