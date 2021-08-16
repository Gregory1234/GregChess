package gregc.gregchess.chess

import gregc.gregchess.chess.component.*
import gregc.gregchess.chess.variant.ChessVariant
import java.time.LocalDateTime
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.safeCast


class GameSettings(
    val name: String,
    val simpleCastling: Boolean,
    val variant: ChessVariant,
    val components: Collection<ComponentData<*>>
) {
    inline fun <reified T : ComponentData<*>> getComponent(): T? = components.filterIsInstance<T>().firstOrNull()
}

enum class TurnEvent(val ending: Boolean) : ChessEvent {
    START(false), END(true), UNDO(true)
}

enum class GameBaseEvent : ChessEvent {
    START,
    RUNNING,
    UPDATE,
    STOP,
    PANIC
}

class ChessGame(val settings: GameSettings, val playerinfo: BySides<ChessPlayerInfo>, val uuid: UUID = UUID.randomUUID()) {

    override fun toString() = "ChessGame(uuid=$uuid)"

    val variant = settings.variant

    val components = settings.components.map { it.getComponent(this) }

    init {
        try {
            requireComponent<Chessboard>()
            for (it in variant.requiredComponents) {
                components.filterIsInstance(it.java).firstOrNull() ?: throw ComponentNotFoundException(it)
            }
            components.forEach { it.validate() }
        } catch (e: Exception) {
            panic(e)
            throw e
        }
    }

    val board get() = requireComponent<Chessboard>()

    val clock get() = getComponent<ChessClock>()

    fun <T : Component> getComponent(cl: KClass<T>): T? =
        components.mapNotNull { cl.safeCast(it) }.firstOrNull()

    fun <T : Component> requireComponent(cl: KClass<T>): T = getComponent(cl) ?: throw ComponentNotFoundException(cl)

    inline fun <reified T : Component> getComponent(): T? = getComponent(T::class)

    inline fun <reified T : Component> requireComponent(): T = requireComponent(T::class)

    val players = bySides { playerinfo[it].getPlayer(it, this) }

    private var state: GameState = GameState.Initial

    private inline fun <reified T> require(): T = (state as? T) ?: run {
        val e = WrongStateException(state, T::class.java)
        if (state !is GameState.Stopped && state !is GameState.Running) {
            panic(e)
        } else {
            stop(drawBy(EndReason.ERROR))
        }
        throw e
    }

    private fun requireInitial() = require<GameState.Initial>()

    private fun requireRunning() = require<GameState.Running>()

    private fun requireStopped() = require<GameState.Stopped>()

    fun callEvent(e: ChessEvent) = components.forEach { it.callEvent(e) }

    var currentTurn: Side = board.initialFEN.currentTurn

    val currentPlayer: ChessPlayer get() = players[currentTurn]

    val currentOpponent: ChessPlayer get() = players[!currentTurn]

    val startTime: LocalDateTime
        get() = require<GameState.WithStartTime>().startTime

    val running: Boolean
        get() = state.running

    fun nextTurn() {
        requireRunning()
        callEvent(TurnEvent.END)
        variant.checkForGameEnd(this)
        if (running) {
            currentTurn++
            startTurn()
        }
    }

    fun previousTurn() {
        requireRunning()
        callEvent(TurnEvent.UNDO)
        currentTurn++
        startPreviousTurn()
    }

    fun start(): ChessGame {
        requireInitial()
        callEvent(GameBaseEvent.START)
        state = GameState.Running()
        callEvent(GameBaseEvent.RUNNING)
        startTurn()
        return this
    }

    fun update() {
        requireRunning()
        callEvent(GameBaseEvent.UPDATE)
    }

    private fun startTurn() {
        requireRunning()
        callEvent(TurnEvent.START)
        currentPlayer.startTurn()
    }

    private fun startPreviousTurn() {
        requireRunning()
        callEvent(TurnEvent.START)
        currentPlayer.startTurn()
    }

    val results: GameResults?
        get() = (state as? GameState.Ended)?.results

    fun stop(results: GameResults) {
        state = GameState.Stopped(state as? GameState.Running ?: run { requireStopped(); return }, results)
        callEvent(GameBaseEvent.STOP)
    }

    private fun panic(e: Exception) {
        e.printStackTrace()
        callEvent(GameBaseEvent.PANIC)
        state = GameState.Error(state, e)
    }

    operator fun get(side: Side): ChessPlayer = players[side]

    fun finishMove(move: MoveCandidate, promotion: Piece?) {
        requireRunning()
        val data = move.execute(promotion)
        board.lastMove?.clear()
        board.lastMove = data
        board.lastMove?.render()
        nextTurn()
    }

}

fun <E> ChessGame.tryOrStopNull(expr: E?): E = try {
    expr!!
} catch (e: NullPointerException) {
    stop(drawBy(EndReason.ERROR))
    throw e
}