package gregc.gregchess.bukkit.match

import gregc.gregchess.*
import gregc.gregchess.bukkit.GregChessPlugin
import gregc.gregchess.bukkit.move.localMoveFormatter
import gregc.gregchess.bukkit.player.*
import gregc.gregchess.bukkit.results.quick
import gregc.gregchess.bukkit.stats.BukkitPlayerStats
import gregc.gregchess.match.*
import gregc.gregchess.move.Move
import gregc.gregchess.results.MatchResults
import gregc.gregchess.stats.VoidPlayerStatsSink
import gregc.gregchess.stats.addStats
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import kotlin.time.Duration.Companion.seconds

enum class PlayerDirection {
    JOIN, LEAVE
}

class PlayerEvent(val player: Player, val dir: PlayerDirection) : ChessEvent {
    override val type get() = BukkitChessEventType.PLAYER
}

@Serializable
class MatchController : Component {

    override val type get() = BukkitComponentType.MATCH_CONTROLLER

    internal var quick: ByColor<Boolean> = byColor(false)
    @Transient
    private var lastPrintedMove: Move? = null

    private fun onStart(match: ChessMatch) {
        ChessMatchManager += match
        match.sideFacades.forEachReal {
            match.callEvent(PlayerEvent(it, PlayerDirection.JOIN))
        }
    }

    private fun onRunning(match: ChessMatch) {
        object : BukkitRunnable() {
            override fun run() {
                if (match.running)
                    match.update()
                else
                    cancel()
            }
        }.runTaskTimer(GregChessPlugin.plugin, 0, 2)
    }

    private fun onStop(match: ChessMatch) {
        val results = match.results!!
        with(match.board) {
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
                match.sideFacades.forEachReal { p ->
                    p.sendLastMoves(match.board.fullmoveCounter + 1, wLast, bLast, match.variant.localMoveFormatter)
                }
            }
        }
        val pgn = PGN.generate(match)
        match.sideFacades.forEachUniqueBukkit { player, color ->
            match.coroutineScope.launch {
                player.showMatchResults(color, results)
                if (!results.endReason.quick)
                    delay((if (quick[color]) 0 else 3).seconds)
                match.callEvent(PlayerEvent(player, PlayerDirection.LEAVE))
                player.sendPGN(pgn)
                player.currentChessMatch = null
            }
        }
        if (!match.sideFacades.isSamePlayer()) {
            match.addStats(byColor {
                val player = match.sides[it]
                if (player is BukkitChessSide)
                    BukkitPlayerStats.of(player.uuid)[it, match.presetName]
                else
                    VoidPlayerStatsSink
            })
        }
        if (!results.endReason.quick)
            match.coroutineScope.launch {
                delay((if (quick.white && quick.black) 0 else 3).seconds)
                ChessMatchManager -= match
            }
        else
            ChessMatchManager -= match
    }

    private fun onPanic(match: ChessMatch) {
        val results = match.results!!
        val pgn = PGN.generate(match)
        match.sideFacades.forEachUniqueBukkit { player, color ->
            player.showMatchResults(color, results)
            player.sendPGN(pgn)
        }
        ChessMatchManager -= match
    }

    override fun init(match: ChessMatch, eventManager: ChessEventManager) {
        eventManager.registerEvent(ChessEventType.BASE) {
            when (it) {
                ChessBaseEvent.START -> onStart(match)
                ChessBaseEvent.RUNNING -> onRunning(match)
                ChessBaseEvent.STOP -> onStop(match)
                ChessBaseEvent.PANIC -> onPanic(match)
                ChessBaseEvent.SYNC -> if (match.state == ChessMatch.State.RUNNING) {
                    onStart(match)
                    onRunning(match)
                } else Unit
                ChessBaseEvent.UPDATE -> Unit
                ChessBaseEvent.CLEAR -> Unit
            }
        }
        eventManager.registerEventE(TurnEvent.END) { handleTurnEnd(match) }
        eventManager.registerEventR(BukkitChessEventType.PLAYER) {
            when(dir) {
                PlayerDirection.JOIN -> player.currentChessSide!!.sendStartMessage()
                PlayerDirection.LEAVE -> {}
            }
        }
    }

    private fun handleTurnEnd(match: ChessMatch) {
        if (match.board.currentTurn == Color.BLACK) {
            with(match.board) {
                val normalMoves = moveHistory.filter { !it.isPhantomMove }
                val wLast = if (normalMoves.size <= 1) null else normalMoves[normalMoves.size - 2]
                val bLast = normalMoves.last()
                match.sideFacades.forEachReal { p ->
                    p.sendLastMoves(match.board.fullmoveCounter, wLast, bLast, match.variant.localMoveFormatter)
                }
                lastPrintedMove = normalMoves.last()
            }
        }
        (match.currentSide as? BukkitChessSideFacade)?.let(GregChessPlugin::clearRequests)
    }
}

val ChessMatch.matchController get() = require(BukkitComponentType.MATCH_CONTROLLER)

fun ChessMatch.stop(results: MatchResults, quick: ByColor<Boolean>) {
    matchController.quick = if ((quick.white || quick.black) && sideFacades.isSamePlayer()) byColor(true) else quick
    stop(results)
}

fun ChessMatch.quickStop(results: MatchResults) = stop(results, byColor(true))