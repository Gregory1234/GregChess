package gregc.gregchess

import gregc.gregchess.chess.human
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent

object BukkitRequestManager : Listener, RequestManager {

    private val requestTypes = mutableListOf<RequestType>()

    fun start() {
        registerEvents()
    }

    override fun register(c: RequestTypeConfig, accept: String, cancel: String): RequestType {
        val requestType = RequestType(BukkitTimeManager, RequestTypeData(c, accept, cancel))
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