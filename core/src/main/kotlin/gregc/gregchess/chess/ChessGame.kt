@file:UseSerializers(InstantSerializer::class)

package gregc.gregchess.chess

import gregc.gregchess.*
import gregc.gregchess.chess.component.*
import gregc.gregchess.chess.move.*
import gregc.gregchess.chess.piece.*
import gregc.gregchess.chess.player.*
import gregc.gregchess.chess.variant.ChessVariant
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*


enum class TurnEvent(val ending: Boolean) : ChessEvent {
    START(false), END(true), UNDO(true)
}

enum class GameBaseEvent : ChessEvent {
    START,
    SYNC,
    RUNNING,
    UPDATE,
    STOP,
    PANIC,
    CLEAR
}

class ChessEventException(val event: ChessEvent, cause: Throwable? = null) : RuntimeException(event.toString(), cause)

interface ComponentHolder {
    operator fun <T : Component> get(type: ComponentType<T>): T?
    fun <T : Component> require(type: ComponentType<T>): T = get(type) ?: throw ComponentNotFoundException(type)
}

@Serializable
class ChessGame private constructor(
    @Contextual val environment: ChessEnvironment,
    val variant: ChessVariant,
    val components: List<Component>, // TODO: serialize as a map instead
    @SerialName("players") val playerData: ByColor<ChessPlayer>,
    @Contextual val uuid: UUID,
    @SerialName("state") private var state_: State,
    @SerialName("startTime") private var startTime_: Instant?, // TODO: add a way to track true game length and true length of each turn
    @SerialName("endTime") private var endTime_: Instant?,
    @SerialName("results") private var results_: GameResults?,
    var currentTurn: Color
) : ComponentHolder {
    constructor(environment: ChessEnvironment, variant: ChessVariant, components: Collection<Component>, playerInfo: ByColor<ChessPlayer>)
            : this(environment, variant, components.toList(), playerInfo, UUID.randomUUID(), State.INITIAL, null, null, null, components.filterIsInstance<Chessboard>().firstOrNull()?.initialFEN?.currentTurn ?: Color.WHITE)

    override fun toString() = "ChessGame(uuid=$uuid)"

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
            CoroutineName("Game $uuid") +
            CoroutineExceptionHandler { _, e ->
                e.printStackTrace()
            }
        )
    }

    val board get() = require(ComponentType.CHESSBOARD)

    @Suppress("UNCHECKED_CAST")
    override fun <T : Component> get(type: ComponentType<T>): T? = components.firstOrNull { it.type == type } as T?

    override fun <T : Component> require(type: ComponentType<T>): T = get(type) ?: throw ComponentNotFoundException(type)

    @Transient
    @Suppress("UNCHECKED_CAST")
    val sides: ByColor<ChessSide<*>> = byColor { playerData[it].initSide(it, this@ChessGame) }

    private fun requireState(s: State) = check(state == s)

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
        variant.checkForGameEnd(this)
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
        callEvent(GameBaseEvent.SYNC)
        callEvent(AddPieceHoldersEvent(pieceHolders))
    }

    fun start() = apply {
        requireState(State.INITIAL)
        callEvent(GameBaseEvent.START)
        callEvent(AddPieceHoldersEvent(pieceHolders))
        sides.forEach(ChessSide<*>::start)
        state = State.RUNNING
        startTime = Instant.now(environment.clock)
        callEvent(GameBaseEvent.RUNNING)
        startTurn()
    }

    fun update() = try {
        requireState(State.RUNNING)
        callEvent(GameBaseEvent.UPDATE)
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

    var results: GameResults?
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

    fun stop(results: GameResults) {
        requireState(State.RUNNING)
        state = State.STOPPED
        this.results = results
        endTime = Instant.now(environment.clock)
        sides.forEach(ChessSide<*>::stop)
        callEvent(GameBaseEvent.STOP)
        joinAllAndThen {
            coroutineScope.cancel()
            sides.forEach(ChessSide<*>::clear)
            callEvent(GameBaseEvent.CLEAR)
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
            callEvent(GameBaseEvent.PANIC)
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

    @Transient
    private val pieceHolders = mutableMapOf<PlacedPieceType<*>, PieceHolder<*>>()

    @Suppress("UNCHECKED_CAST")
    private operator fun get(p: PlacedPieceType<*>): PieceHolder<PlacedPiece> = pieceHolders[p]!! as PieceHolder<PlacedPiece>

    private inner class GameMoveEnvironment : ChessboardView by board, MoveEnvironment {
        override fun checkExists(p: PlacedPiece) = this@ChessGame[p.placedPieceType].checkExists(p)
        override fun checkCanExist(p: PlacedPiece) = this@ChessGame[p.placedPieceType].checkCanExist(p)
        override fun create(p: PlacedPiece) = this@ChessGame[p.placedPieceType].create(p)
        override fun destroy(p: PlacedPiece) = this@ChessGame[p.placedPieceType].destroy(p)

        override val heldPieces get() = pieceHolders.flatMap { it.value.heldPieces }

        @Suppress("UNCHECKED_CAST")
        override fun <T : PlacedPiece> heldPiecesOf(t: PlacedPieceType<T>) = get(t).heldPieces as Collection<T>

        override fun updateMoves() = this@ChessGame.board.updateMoves()
        override fun addFlag(pos: Pos, flag: ChessFlag, age: UInt) = this@ChessGame.board.addFlag(pos, flag, age)
        override fun callEvent(e: ChessEvent) = this@ChessGame.callEvent(e)
        override fun <T : Component> get(type: ComponentType<T>): T? = this@ChessGame[type]

        override val variant: ChessVariant get() = this@ChessGame.variant
    }

    fun finishMove(move: Move) {
        requireState(State.RUNNING)
        move.execute(GameMoveEnvironment())
        board.lastMove = move
        if (!move.isPhantomMove)
            nextTurn()
        else
            variant.checkForGameEnd(this)
    }

    fun undoMove(move: Move) {
        requireState(State.RUNNING)
        move.undo(GameMoveEnvironment())
    }

}