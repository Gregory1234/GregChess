package gregc.gregchess.bukkit.match

import gregc.gregchess.ByColor
import gregc.gregchess.bukkit.GregChessPlugin
import gregc.gregchess.bukkit.player.*
import gregc.gregchess.bukkit.results.sendMatchResults
import gregc.gregchess.bukkit.stats.BukkitPlayerStats
import gregc.gregchess.byColor
import gregc.gregchess.match.*
import gregc.gregchess.results.MatchResults
import gregc.gregchess.stats.VoidPlayerStatsSink
import gregc.gregchess.stats.addStats
import kotlinx.serialization.Serializable
import org.bukkit.scheduler.BukkitRunnable

enum class PlayerDirection {
    JOIN, LEAVE
}

class PlayerEvent(val player: BukkitPlayer, val dir: PlayerDirection) : ChessEvent {
    override val type get() = BukkitChessEventType.PLAYER
}

@Serializable
object MatchController : Component {

    override val type get() = BukkitComponentType.MATCH_CONTROLLER

    private fun onStart(match: ChessMatch) {
        ChessMatchManager += match
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
        if (!match.sideFacades.isSamePlayer()) {
            match.addStats(byColor {
                val player = match.sides[it]
                if (player is BukkitChessSide)
                    BukkitPlayerStats.of(player.uuid)[it, match.presetName]
                else
                    VoidPlayerStatsSink
            })
        }
    }

    private fun onClear(match: ChessMatch) {
        ChessMatchManager -= match
    }

    private fun onPanic(match: ChessMatch) {
        val results = match.results!!
        val pgn = PGN.generate(match)
        match.sideFacades.forEachUnique { player ->
            player.player.sendMatchResults(player.color, results)
            player.player.sendMessage(pgn.copyMessage())
        }
        ChessMatchManager -= match
    }

    override fun init(match: ChessMatch, events: ChessEventRegistry) {
        events.register(ChessEventType.BASE, ChessEventOrderConstraint(runBeforeAll = true)) {
            when (it) {
                ChessBaseEvent.START -> onStart(match)
                ChessBaseEvent.RUNNING -> onRunning(match)
                ChessBaseEvent.SYNC -> if (match.running) {
                    onStart(match)
                    onRunning(match)
                }
                else -> {}
            }
        }
        events.register(ChessEventType.BASE, ChessEventOrderConstraint(runAfterAll = true)) {
            when (it) {
                ChessBaseEvent.STOP -> onStop(match)
                ChessBaseEvent.CLEAR -> onClear(match)
                ChessBaseEvent.PANIC -> onPanic(match)
                else -> {}
            }
        }
    }
}

val ChessMatch.matchController get() = require(BukkitComponentType.MATCH_CONTROLLER)

fun ChessMatch.stop(results: MatchResults, quick: ByColor<Boolean>) {
    sideFacades.forEach {
        if (it is BukkitChessSideFacade) {
            it.side.quick = quick[it.color]
        }
    }
    stop(results)
}

fun ChessMatch.quickStop(results: MatchResults) = stop(results, byColor(true))