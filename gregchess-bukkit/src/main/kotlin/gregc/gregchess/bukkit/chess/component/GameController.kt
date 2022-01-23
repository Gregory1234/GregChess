package gregc.gregchess.bukkit.chess.component

import gregc.gregchess.bukkit.GregChessPlugin
import gregc.gregchess.bukkit.chess.*
import gregc.gregchess.bukkit.chess.player.*
import gregc.gregchess.bukkitutils.ticks
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Component
import gregc.gregchess.chess.move.Move
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

enum class GameStopStageEvent : ChessEvent {
    STOP, CLEAR, VERY_END, PANIC
}

enum class PlayerDirection {
    JOIN, LEAVE
}

class PlayerEvent(val player: Player, val dir: PlayerDirection) : ChessEvent

@Serializable
class GameController : Component {

    override val type get() = BukkitComponentType.GAME_CONTROLLER

    @Transient
    private lateinit var game: ChessGame

    override fun init(game: ChessGame) {
        this.game = game
    }

    internal var quick: ByColor<Boolean> = byColor(false)
    @Transient
    private var lastPrintedMove: Move? = null

    private fun onStart() {
        game.sides.forEachRealBukkit {
            game.callEvent(PlayerEvent(it, PlayerDirection.JOIN))
            it.games += game
            it.currentGame = game
        }
    }

    private fun onRunning() {
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
        game.callEvent(GameStopStageEvent.STOP)
        with(game.board) {
            if (lastPrintedMove != lastMove) {
                val wLast: Move?
                val bLast: Move?
                if (lastMove?.main?.color == Color.WHITE) {
                    wLast = lastMove
                    bLast = null
                } else {
                    wLast = if (moveHistory.size <= 1) null else moveHistory[moveHistory.size - 2]
                    bLast = lastMove
                }
                game.sides.forEachRealBukkit { p ->
                    p.sendLastMoves(game.board.fullmoveCounter + 1u, wLast, bLast, game.variant.localNameFormatter)
                }
            }
        }
        val pgn = PGN.generate(game)
        game.sides.forEachUniqueBukkit { player, color ->
            game.coroutineScope.launch {
                player.showGameResults(color, results)
                if (player.gregchess != game[!color].player) {
                    val stats = ChessStats.of(player.uniqueId)
                    when (results.score) {
                        GameScore.Draw -> stats.addDraw(game.settings.name)
                        GameScore.Victory(color) -> stats.addWin(game.settings.name)
                        else -> stats.addLoss(game.settings.name)
                    }
                }
                if (!results.endReason.quick)
                    delay((if (quick[color]) 0 else 3).seconds)
                game.callEvent(PlayerEvent(player, PlayerDirection.LEAVE))
                player.sendPGN(pgn)
                player.games -= game
                player.currentGame = null
            }
        }
        if (results.endReason.quick) {
            game.callEvent(GameStopStageEvent.CLEAR)
            game.callEvent(GameStopStageEvent.VERY_END)
            return
        }
        game.coroutineScope.launch {
            delay((if (quick.white && quick.black) 0 else 3).seconds)
            delay(1.ticks)
            game.callEvent(GameStopStageEvent.CLEAR)
            delay(1.ticks)
            game.callEvent(GameStopStageEvent.VERY_END)
        }
    }

    private fun onPanic() {
        val results = game.results!!
        val pgn = PGN.generate(game)
        game.sides.forEachUniqueBukkit { player, color ->
            player.showGameResults(color, results)
            player.sendPGN(pgn)
            player.games -= game
            player.currentGame = null
        }
        game.callEvent(GameStopStageEvent.PANIC)
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
        GameBaseEvent.CLEAR -> Unit
    }

    @ChessEventHandler
    fun handleTurn(e: TurnEvent) {
        if (e == TurnEvent.END) {
            if (game.currentTurn == Color.BLACK) {
                with(game.board) {
                    val wLast = if (moveHistory.size <= 1) null else moveHistory[moveHistory.size - 2]
                    val bLast = lastMove
                    game.sides.forEachRealBukkit { p ->
                        p.sendLastMoves(game.board.fullmoveCounter, wLast, bLast, game.variant.localNameFormatter)
                    }
                    lastPrintedMove = lastMove
                }
            }
        }
    }
}

val ChessGame.gameController get() = requireComponent<GameController>()

fun ChessGame.stop(results: GameResults, quick: ByColor<Boolean>) {
    gameController.quick = if ((quick.white || quick.black) && sides.white.player == sides.black.player) byColor(true) else quick
    stop(results)
}

fun ChessGame.quickStop(results: GameResults) = stop(results, byColor(true))