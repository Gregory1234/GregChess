package gregc.gregchess.bukkit.match

import gregc.gregchess.bukkit.GregChessPlugin
import gregc.gregchess.bukkit.player.gregchessPlayer
import gregc.gregchess.bukkit.registerEvents
import gregc.gregchess.match.ChessMatch
import gregc.gregchess.registry.Register
import gregc.gregchess.results.*
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.*
import kotlin.collections.set

object ChessMatchManager : Listener {
    @JvmField
    @Register(data = ["quick"])
    val PLUGIN_DISABLED = DrawEndReason(EndReason.Type.EMERGENCY)

    private val matches = mutableMapOf<UUID, ChessMatch>()

    operator fun get(uuid: UUID): ChessMatch? = matches[uuid]

    fun start() {
        registerEvents()
    }

    fun stop() {
        for (g in matches.values)
            g.stop(drawBy(PLUGIN_DISABLED))
    }

    @EventHandler
    fun onPlayerJoin(e: PlayerJoinEvent) {
        Bukkit.getScheduler().scheduleSyncDelayedTask(GregChessPlugin.plugin, { e.gregchessPlayer.sendRejoinReminder() }, 1)
    }

    @EventHandler
    fun onPlayerLeave(e: PlayerQuitEvent) {
        if (e.gregchessPlayer.isInMatch)
            e.gregchessPlayer.leaveMatch()
        else if (e.gregchessPlayer.isSpectatingMatch)
            e.gregchessPlayer.leaveSpectatedMatch()
    }

    internal operator fun plusAssign(match: ChessMatch) {
        require(match.uuid !in matches)
        matches[match.uuid] = match
    }

    internal operator fun minusAssign(g: ChessMatch) {
        matches.remove(g.uuid, g)
    }

}
