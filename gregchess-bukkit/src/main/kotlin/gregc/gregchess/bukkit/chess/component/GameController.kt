package gregc.gregchess.bukkit.chess.component

import gregc.gregchess.bukkit.GregChessPlugin
import gregc.gregchess.bukkit.chess.localNameFormatter
import gregc.gregchess.bukkit.chess.player.*
import gregc.gregchess.bukkit.chess.quick
import gregc.gregchess.bukkit.ticks
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.SimpleComponent
import gregc.gregchess.chess.player.ChessSide
import kotlinx.coroutines.*
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

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

class GameController(game: ChessGame) : SimpleComponent(game) {

    internal var quick: ByColor<Boolean> = byColor(false)

    private fun onStart() {
        game.sides.forEachRealBukkit {
            callEvent(PlayerEvent(it, PlayerDirection.JOIN))
            it.games += game
            it.currentGame = game
        }
        callEvent(GameStartStageEvent.INIT)
        game.sides.forEachUnique { it.init() }
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
        }.runTaskTimer(GregChessPlugin.plugin, 0, 2)
    }

    @OptIn(ExperimentalTime::class)
    private fun onStop() {
        val results = game.results!!
        callEvent(GameStopStageEvent.STOP)
        with(game.board) {
            if (lastMove?.main?.color == Color.WHITE) {
                val wLast = lastMove
                game.sides.forEachRealBukkit { p ->
                    p.sendLastMoves(fullmoveCounter + 1u, wLast, null, game.variant.localNameFormatter)
                }
            }
        }
        val pgn = PGN.generate(game)
        game.sides.forEachUnique {
            it.bukkit?.let { player ->
                game.coroutineScope.launch {
                    player.showGameResults(it.color, results)
                    if (!results.endReason.quick)
                        delay((if (quick[it.color]) 0 else 3).seconds)
                    callEvent(PlayerEvent(player, PlayerDirection.LEAVE))
                    player.sendPGN(pgn)
                    player.games -= game
                    player.currentGame = null
                }
            }
        }
        if (results.endReason.quick) {
            callEvent(GameStopStageEvent.CLEAR)
            game.sides.forEach(ChessSide<*>::stop)
            callEvent(GameStopStageEvent.VERY_END)
            game.coroutineScope.cancel()
            return
        }
        game.coroutineScope.launch {
            delay((if (quick.white && quick.black) 0 else 3).seconds)
            delay(1.ticks)
            callEvent(GameStopStageEvent.CLEAR)
            delay(1.ticks)
            game.sides.forEach(ChessSide<*>::stop)
            callEvent(GameStopStageEvent.VERY_END)
        }.invokeOnCompletion {
            game.coroutineScope.cancel()
            if (it != null)
                throw it
        }
    }

    private fun onPanic() {
        game.sides.forEach(ChessSide<*>::stop)
        val results = game.results!!
        val pgn = PGN.generate(game)
        game.sides.forEachUnique {
            it.bukkit?.let { player ->
                player.showGameResults(it.color, results)
                player.sendPGN(pgn)
                player.games -= game
                player.currentGame = null
            }
        }
        callEvent(GameStopStageEvent.PANIC)
        game.coroutineScope.cancel()
    }

    @ChessEventHandler
    fun handleEvents(e: GameBaseEvent) = when (e) {
        GameBaseEvent.START -> onStart()
        GameBaseEvent.RUNNING -> onRunning()
        GameBaseEvent.STOP -> onStop()
        GameBaseEvent.PANIC -> onPanic()
        GameBaseEvent.SYNC -> if (game.state == ChessGame.State.RUNNING) {
            // TODO: make sync safer
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
                    game.sides.forEachRealBukkit { p ->
                        p.sendLastMoves(game.board.fullmoveCounter, wLast, bLast, game.variant.localNameFormatter)
                    }
                }
            }
        }
    }
}

val ChessGame.gameController get() = requireComponent<GameController>()

fun ChessGame.stop(results: GameResults, quick: ByColor<Boolean>) {
    gameController.quick = quick
    stop(results)
}

fun ChessGame.quickStop(results: GameResults) = stop(results, byColor(true))