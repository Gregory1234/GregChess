package gregc.gregchess.bukkit.component

import gregc.gregchess.bukkit.GregChessPlugin
import gregc.gregchess.bukkit.match.ChessMatchManager
import gregc.gregchess.bukkit.match.presetName
import gregc.gregchess.bukkit.player.BukkitChessSideFacade
import gregc.gregchess.bukkit.player.isSamePlayer
import gregc.gregchess.bukkit.stats.BukkitPlayerStats
import gregc.gregchess.byColor
import gregc.gregchess.component.Component
import gregc.gregchess.event.*
import gregc.gregchess.match.ChessMatch
import gregc.gregchess.stats.VoidPlayerStatsSink
import gregc.gregchess.stats.addStats
import kotlinx.serialization.Serializable
import org.bukkit.scheduler.BukkitRunnable

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
        if (!match.sides.isSamePlayer()) {
            match.addStats(byColor {
                val player = match.sides[it]
                if (player is BukkitChessSideFacade)
                    BukkitPlayerStats.of(player.uuid)[it, match.presetName]
                else
                    VoidPlayerStatsSink
            })
        }
    }

    private fun onClear(match: ChessMatch) {
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
                ChessBaseEvent.CLEAR, ChessBaseEvent.PANIC -> onClear(match)
                else -> {}
            }
        }
    }
}