package gregc.gregchess.bukkit.chess.component

import gregc.gregchess.bukkit.GregChess
import gregc.gregchess.bukkit.chess.*
import gregc.gregchess.bukkit.coroutines.BukkitContext
import gregc.gregchess.bukkit.coroutines.BukkitScope
import gregc.gregchess.bukkit.ticks
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.SimpleComponent
import gregc.gregchess.seconds
import kotlinx.coroutines.*
import kotlinx.coroutines.time.delay
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import kotlin.coroutines.CoroutineContext

enum class GameStartStageEvent : ChessEvent {
    INIT, START, BEGIN
}

enum class GameStopStageEvent : ChessEvent {
    STOP, CLEAR, VERY_END, PANIC
}

enum class PlayerDirection {
    JOIN, LEAVE
}

class PlayerEvent(val player: Player, val dir: PlayerDirection) : ChessEvent

// TODO: simplify or rename
class PlayerManager(
    game: ChessGame,
) : SimpleComponent(game), CoroutineScope {

    override val coroutineContext: CoroutineContext = BukkitScope(GregChess.plugin, BukkitContext.SYNC).coroutineContext

    internal var quick: ByColor<Boolean> = byColor(false)

    private fun onStart() {
        game.players.forEachReal {
            callEvent(PlayerEvent(it, PlayerDirection.JOIN))
            it.games += game
            it.currentGame = game
        }
        callEvent(GameStartStageEvent.INIT)
        game.players.forEachUnique { it.init() }
        callEvent(GameStartStageEvent.START)
    }

    private fun onRunning() {
        callEvent(GameStartStageEvent.BEGIN)
        object : BukkitRunnable() {
            override fun run() {
                if (game.running)
                    game.update()
                else
                    cancel()
            }
        }.runTaskTimer(GregChess.plugin, 0, 2)
    }

    private fun onStop() {
        val results = game.results!!
        callEvent(GameStopStageEvent.STOP)
        with(game.board) {
            if (lastMove?.piece?.color == Color.WHITE) {
                val wLast = lastMove
                game.players.forEachReal { p ->
                    p.sendLastMoves(fullmoveCounter + 1u, wLast, null)
                }
            }
        }
        val pgn = PGN.generate(game)
        game.players.forEachUnique {
            launch {
                it.player.showGameResults(it.color, results)
                if (!results.endReason.quick)
                    delay((if (quick[it.color]) 0 else 3).seconds)
                callEvent(PlayerEvent(it.player, PlayerDirection.LEAVE))
                it.player.sendPGN(pgn)
                it.player.games -= game
                it.player.currentGame = null
            }
        }
        if (results.endReason.quick) {
            callEvent(GameStopStageEvent.CLEAR)
            game.players.forEach(ChessPlayer::stop)
            callEvent(GameStopStageEvent.VERY_END)
            return
        }
        launch {
            delay((if (quick.white && quick.black) 0 else 3).seconds)
            delay(1.ticks)
            callEvent(GameStopStageEvent.CLEAR)
            delay(1.ticks)
            game.players.forEach(ChessPlayer::stop)
            callEvent(GameStopStageEvent.VERY_END)
            cancel()
        }
    }

    private fun onPanic() {
        game.players.forEach(ChessPlayer::stop)
        val results = game.results!!
        val pgn = PGN.generate(game)
        game.players.forEachUnique {
            it.player.showGameResults(it.color, results)
            it.player.sendPGN(pgn)
            it.player.games -= game
            it.player.currentGame = null
        }
        callEvent(GameStopStageEvent.PANIC)
        cancel()
    }

    @ChessEventHandler
    fun handleEvents(e: GameBaseEvent) = when (e) {
        GameBaseEvent.START -> onStart()
        GameBaseEvent.RUNNING -> onRunning()
        GameBaseEvent.STOP -> onStop()
        GameBaseEvent.PANIC -> onPanic()
        GameBaseEvent.SYNC -> if (game.state == ChessGame.State.RUNNING) {
            onStart()
            onRunning()
        } else Unit
        GameBaseEvent.UPDATE -> Unit
    }

    @ChessEventHandler
    fun handleTurn(e: TurnEvent) {
        if (e == TurnEvent.END) {
            if (game.currentTurn == Color.BLACK) {
                with(game.board) {
                    val wLast = (if (moveHistory.size <= 1) null else moveHistory[moveHistory.size - 2])
                    val bLast = lastMove
                    game.players.forEachReal { p ->
                        p.sendLastMoves(game.board.fullmoveCounter, wLast, bLast)
                    }
                }
            }
        }
    }
}

val ChessGame.playerManager get() = requireComponent<PlayerManager>()

fun ChessGame.stop(results: GameResults, quick: ByColor<Boolean>) {
    playerManager.quick = quick
    stop(results)
}

fun ChessGame.quickStop(results: GameResults) = stop(results, byColor(true))