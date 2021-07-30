package gregc.gregchess.chess

import gregc.gregchess.chess.component.*
import gregc.gregchess.chess.variant.ChessVariant
import gregc.gregchess.upperFirst
import java.time.LocalDateTime
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.safeCast


class GameSettings(
    val name: String,
    val simpleCastling: Boolean,
    val variant: ChessVariant,
    val components: Collection<Component.Settings<*>>
) {
    inline fun <reified T : Component.Settings<*>> getComponent(): T? = components.filterIsInstance<T>().firstOrNull()
}

enum class TurnEvent(val ending: Boolean): ChessEvent {
    START(false), END(true), UNDO(true)
}

enum class GameBaseEvent: ChessEvent {
    PRE_INIT,
    START,
    RUNNING,
    UPDATE,
    STOP,
    STOPPED,
    PANIC
}

class ChessGame(val settings: GameSettings, val uuid: UUID = UUID.randomUUID()) {

    override fun toString() = "ChessGame(uuid=$uuid)"

    val variant = settings.variant

    val components = settings.components.map { it.getComponent(this) }

    init {
        try {
            requireComponent<Chessboard>()
            variant.requiredComponents.forEach {
                settings.components.filterIsInstance(it.java).firstOrNull() ?: throw ComponentSettingsNotFoundException(it)
            }
            components.callEvent(GameBaseEvent.PRE_INIT)
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

    private var state: GameState = GameState.Initial

    private inline fun <reified T> require(): T = (state as? T) ?: run {
        val e = WrongStateException(state, T::class.java)
        if (state !is GameState.Stopping && state !is GameState.Running) {
            panic(e)
        } else {
            stop(drawBy(EndReason.ERROR))
        }
        throw e
    }

    private fun requireInitial() = require<GameState.Initial>()

    private fun requireReady() = require<GameState.Ready>()

    private fun requireStarting() = require<GameState.Starting>()

    private fun requireRunning() = require<GameState.Running>()

    private fun requireStopping() = require<GameState.Stopping>()

    var currentTurn: Side
        get() = require<GameState.WithCurrentPlayer>().currentTurn
        set(v) {
            requireRunning().currentTurn = v
        }

    val currentPlayer: ChessPlayer
        get() = require<GameState.WithCurrentPlayer>().currentPlayer

    val currentOpponent: ChessPlayer
        get() = require<GameState.WithCurrentPlayer>().currentOpponent

    inner class AddPlayersScope {
        private val players = MutableBySides<ChessPlayer?>(null)

        fun addPlayer(p: ChessPlayer) {
            if (players[p.side] != null)
                throw IllegalStateException("${p.side.name.lowercase().upperFirst()} player already added")
            players[p.side] = p
        }

        fun human(p: HumanPlayer, side: Side, silent: Boolean) =
            addPlayer(HumanChessPlayer(p, side, silent, this@ChessGame))

        fun engine(engine: ChessEngine, side: Side) =
            addPlayer(EnginePlayer(engine, side, this@ChessGame))

        fun build(): BySides<ChessPlayer> = players.map {
            it ?: throw IllegalStateException("player has not been initialized")
        }

    }

    fun addPlayers(init: AddPlayersScope.() -> Unit): ChessGame {
        requireInitial()
        state = GameState.Ready(AddPlayersScope().run {
            init()
            build()
        })
        return this
    }

    val startTime: LocalDateTime
        get() = require<GameState.WithStartTime>().startTime

    val running: Boolean
        get() = state.running

    val players: BySides<ChessPlayer>
        get() = require<GameState.WithPlayers>().players

    operator fun contains(p: HumanPlayer): Boolean = require<GameState.WithPlayers>().contains(p)

    fun nextTurn() {
        requireRunning()
        components.callEvent(TurnEvent.END)
        variant.checkForGameEnd(this)
        if (running) {
            currentTurn++
            startTurn()
        }
    }

    fun previousTurn() {
        requireRunning()
        components.callEvent(TurnEvent.UNDO)
        currentTurn++
        startPreviousTurn()
    }

    fun start(): ChessGame {
        state = GameState.Starting(requireReady())
        components.callEvent(GameBaseEvent.START)
        state = GameState.Running(requireStarting())
        components.callEvent(GameBaseEvent.RUNNING)
        startTurn()
        return this
    }

    fun update() {
        requireRunning()
        components.callEvent(GameBaseEvent.UPDATE)
    }

    private fun startTurn() {
        requireRunning()
        components.callEvent(TurnEvent.START)
        currentPlayer.startTurn()
    }

    private fun startPreviousTurn() {
        requireRunning()
        components.callEvent(TurnEvent.START)
        currentPlayer.startTurn()
    }

    val results: GameResults<*>?
        get() = (state as? GameState.Ended)?.results

    fun stop(results: GameResults<*>) {
        state = GameState.Stopping(state as? GameState.Running ?: run { requireStopping(); return }, results)
        components.callEvent(GameBaseEvent.STOP)
    }

    fun finishStopping() {
        state = GameState.Stopped(requireStopping())
        components.callEvent(GameBaseEvent.STOPPED)
    }

    private fun panic(e: Exception) {
        e.printStackTrace()
        components.callEvent(GameBaseEvent.PANIC)
        state = GameState.Error(state, e)
    }

    operator fun get(player: HumanPlayer): HumanChessPlayer? = (state as? GameState.WithCurrentPlayer)?.get(player)

    operator fun get(side: Side): ChessPlayer = require<GameState.WithPlayers>()[side]

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