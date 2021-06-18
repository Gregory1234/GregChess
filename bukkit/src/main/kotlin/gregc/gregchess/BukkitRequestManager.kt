package gregc.gregchess

import gregc.gregchess.chess.human
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin

class BukkitRequestManager(private val plugin: Plugin, private val timeManager: TimeManager) : Listener,
    RequestManager {

    private val requestTypes = mutableListOf<RequestType>()

    fun start() {
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }

    override fun register(c: RequestTypeConfig, accept: String, cancel: String): RequestType {
        val requestType = RequestType(timeManager, RequestTypeData(c, accept, cancel))
        requestTypes.add(requestType)
        return requestType
    }

    @EventHandler
    fun onPlayerQuit(e: PlayerQuitEvent) {
        requestTypes.forEach {
            it.quietRemove(e.player.human)
        }
    }
}