package gregc.gregchess.bukkit.match

import gregc.gregchess.bukkit.GregChessPlugin
import gregc.gregchess.bukkit.player.*
import gregc.gregchess.bukkit.stats.BukkitPlayerStats
import gregc.gregchess.byColor
import gregc.gregchess.match.*
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