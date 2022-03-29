package gregc.gregchess.bukkit.chess.component

import gregc.gregchess.bukkit.BukkitRegistering
import gregc.gregchess.bukkit.GregChessPlugin
import gregc.gregchess.bukkit.chess.*
import gregc.gregchess.bukkit.chess.player.*
import gregc.gregchess.chess.*
import gregc.gregchess.game.*
import gregc.gregchess.move.Move
import gregc.gregchess.util.Register
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

enum class PlayerDirection {
    JOIN, LEAVE
}

class PlayerEvent(val player: Player, val dir: PlayerDirection) : ChessEvent

@Serializable
class GameController(val presetName: String) : Component {

    companion object : BukkitRegistering {
        @JvmField
        @Register
        val PRESET = PropertyType()
    }

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
        game.sides.forEachReal {
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
        with(game.board) {
            val normalMoves = moveHistory.filter { !it.isPhantomMove }
            if (lastPrintedMove != normalMoves.lastOrNull()) {
                val wLast: Move?
                val bLast: Move?
                if (normalMoves.lastOrNull()?.main?.color == Color.WHITE) {
                    wLast = normalMoves.lastOrNull()
                    bLast = null
                } else {
                    wLast = if (normalMoves.size <= 1) null else normalMoves[normalMoves.size - 2]
                    bLast = normalMoves.lastOrNull()
                }
                game.sides.forEachReal { p ->
                    p.sendLastMoves(game.board.fullmoveCounter + 1, wLast, bLast, game.variant.localMoveFormatter)
                }
            }
        }
        val pgn = PGN.generate(game)
        game.sides.forEachUniqueBukkit { player, color ->
            game.coroutineScope.launch {
                player.showGameResults(color, results)
                if (!results.endReason.quick)
                    delay((if (quick[color]) 0 else 3).seconds)
                game.callEvent(PlayerEvent(player, PlayerDirection.LEAVE))
                player.sendPGN(pgn)
                player.games -= game
                player.currentGame = null
            }
        }
        if (game.sides.white.player != game.sides.black.player) {
            game.addStats(byColor {
                val player = game.sides[it]
                if (player is BukkitChessSide)
                    BukkitPlayerStats.of(player.uuid)[it, presetName]
                else
                    VoidPlayerStatsSink
            })
        }
        if (!results.endReason.quick)
            game.coroutineScope.launch {
                delay((if (quick.white && quick.black) 0 else 3).seconds)
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
        GameBaseEvent.CLEAR -> Unit
    }

    @ChessEventHandler
    fun handleTurn(e: TurnEvent) {
        if (e == TurnEvent.END) {
            if (game.currentTurn == Color.BLACK) {
                with(game.board) {
                    val normalMoves = moveHistory.filter { !it.isPhantomMove }
                    val wLast = if (normalMoves.size <= 1) null else normalMoves[normalMoves.size - 2]
                    val bLast = normalMoves.last()
                    game.sides.forEachReal { p ->
                        p.sendLastMoves(game.board.fullmoveCounter, wLast, bLast, game.variant.localMoveFormatter)
                    }
                    lastPrintedMove = normalMoves.last()
                }
            }
        }
    }

    @ChessEventHandler
    fun addProperties(e: AddPropertiesEvent) {
        e.game(PRESET) { presetName }
    }
}

val ChessGame.gameController get() = require(BukkitComponentType.GAME_CONTROLLER)
val ComponentHolder.gameController get() = get(BukkitComponentType.GAME_CONTROLLER)

fun ChessGame.stop(results: GameResults, quick: ByColor<Boolean>) {
    gameController.quick = if ((quick.white || quick.black) && sides.white.player == sides.black.player) byColor(true) else quick
    stop(results)
}

fun ChessGame.quickStop(results: GameResults) = stop(results, byColor(true))