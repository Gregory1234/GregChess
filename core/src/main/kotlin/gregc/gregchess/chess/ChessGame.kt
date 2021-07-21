package gregc.gregchess.chess

import gregc.gregchess.*
import gregc.gregchess.chess.component.*
import gregc.gregchess.chess.variant.ChessVariant
import java.time.LocalDateTime
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.safeCast


data class GameSettings(
    val name: String,
    val simpleCastling: Boolean,
    val variant: ChessVariant,
    val components: Collection<Component.Settings<*>>
) {
    inline fun <reified T : Component.Settings<*>> getComponent(): T? = components.filterIsInstance<T>().firstOrNull()
}

enum class PlayerDirection {
    JOIN, LEAVE
}

data class HumanPlayerEvent(val human: HumanPlayer, val dir: PlayerDirection): ChessEvent

enum class TurnEvent(val ending: Boolean): ChessEvent {
    START(false), END(true), UNDO(true)
}

enum class GameBaseEvent: ChessEvent {
    PRE_INIT,
    INIT,
    START,
    BEGIN,
    UPDATE,
    STOP,
    CLEAR,
    VERY_END,
    PANIC
}

class ChessGame(private val timeManager: TimeManager, val settings: GameSettings) {

    val uniqueId: UUID = UUID.randomUUID()

    override fun toString() = "ChessGame(uniqueId=$uniqueId)"

    val variant = settings.variant

    val components = settings.components.map { it.getComponent(this) }

    init {
        try {
            requireComponent<Chessboard>()
            requireComponent<ScoreboardManager>()
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

    val scoreboard get() = requireComponent<ScoreboardManager>()

    fun <T : Component> getComponent(cl: KClass<T>): T? =
        components.mapNotNull { cl.safeCast(it) }.firstOrNull()

    fun <T : Component> requireComponent(cl: KClass<T>): T = getComponent(cl) ?: throw ComponentNotFoundException(cl)

    inline fun <reified T : Component> getComponent(): T? = getComponent(T::class)

    inline fun <reified T : Component> requireComponent(): T = requireComponent(T::class)

    private var state: GameState = GameState.Initial

    private inline fun <reified T> require(): T = (state as? T) ?: run {
        val e = WrongStateException(state, T::class.java)
        stop(EndReason.Error(e))
        throw e
    }

    private fun requireInitial() = require<GameState.Initial>()

    private fun requireReady() = require<GameState.Ready>()

    private fun requireStarting() = require<GameState.Starting>()

    private fun requireRunning() = require<GameState.Running>()

    private fun requireStopping() = (state as? GameState.Stopping) ?: run {
        val e = WrongStateException(state, GameState.Stopping::class.java)
        panic(e)
        throw e
    }

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
        try {
            state = GameState.Starting(requireReady())
            players.forEachReal {
                components.callEvent(HumanPlayerEvent(it, PlayerDirection.JOIN))
            }
            components.callEvent(GameBaseEvent.INIT)
            requireStarting().forEachUnique { it.init() }
            variant.start(this)
            components.callEvent(GameBaseEvent.START)
            state = GameState.Running(requireStarting())
            glog.mid("Started game", uniqueId)
        } catch (e: Exception) {
            panic(e)
            glog.mid("Failed to start game", uniqueId)
            throw e
        }
        components.callEvent(GameBaseEvent.BEGIN)
        timeManager.runTaskTimer(0.seconds, 0.1.seconds) {
            if (!running)
                cancel()
            else
                components.callEvent(GameBaseEvent.UPDATE)
        }
        startTurn()
        return this
    }

    private fun startTurn() {
        requireRunning()
        components.callEvent(TurnEvent.START)
        currentPlayer.startTurn()
        glog.low("Started turn", uniqueId, currentTurn)
    }

    private fun startPreviousTurn() {
        requireRunning()
        components.callEvent(TurnEvent.START)
        currentPlayer.startTurn()
        glog.low("Started previous turn", uniqueId, currentTurn)
    }

    val endReason: EndReason?
        get() = (state as? GameState.WithEndReason)?.endReason

    fun quickStop(reason: EndReason) = stop(reason, BySides(true))

    fun stop(reason: EndReason, quick: BySides<Boolean> = BySides(false)) {
        val stopping = GameState.Stopping(state as? GameState.Running ?: run { requireStopping(); return }, reason)
        state = stopping
        try {
            components.callEvent(GameBaseEvent.STOP)
            players.forEachUnique(currentTurn) {
                interact {
                    it.player.showEndReason(it.side, reason)
                    if (!reason.quick)
                        timeManager.wait((if (quick[it.side]) 0 else 3).seconds)
                    components.callEvent(HumanPlayerEvent(it.player, PlayerDirection.LEAVE))
                }
            }
            if (reason.quick) {
                components.callEvent(GameBaseEvent.CLEAR)
                players.forEach(ChessPlayer::stop)
                state = GameState.Stopped(stopping)
                glog.low("Stopped game", uniqueId, reason)
                components.callEvent(GameBaseEvent.VERY_END)
                return
            }
            interact {
                timeManager.wait((if (quick.white && quick.black) 0 else 3).seconds)
                timeManager.waitTick()
                components.callEvent(GameBaseEvent.CLEAR)
                timeManager.waitTick()
                players.forEach(ChessPlayer::stop)
                state = GameState.Stopped(stopping)
                glog.low("Stopped game", uniqueId, reason)
                components.callEvent(GameBaseEvent.VERY_END)
            }
        } catch (e: Exception) {
            panic(e)
            throw e
        }
    }

    private fun panic(e: Exception) {
        e.printStackTrace()
        components.callEvent(GameBaseEvent.PANIC)
        state = GameState.Error(state, e)
    }

    operator fun get(player: HumanPlayer): HumanChessPlayer? = (state as? GameState.WithCurrentPlayer)?.get(player)

    operator fun get(side: Side): ChessPlayer = require<GameState.WithPlayers>()[side]

    fun finishMove(move: MoveCandidate) {
        requireRunning()
        val data = move.execute()
        board.lastMove?.clear()
        board.lastMove = data
        board.lastMove?.render()
        glog.low("Finished move", data)
        nextTurn()
    }

}

fun <E> ChessGame.tryOrStopNull(expr: E?): E = try {
    expr!!
} catch (e: NullPointerException) {
    stop(EndReason.Error(e))
    throw e
}