package gregc.gregchess.bukkit.match

import gregc.gregchess.Register
import gregc.gregchess.bukkit.GregChessPlugin
import gregc.gregchess.bukkit.player.gregchessPlayer
import gregc.gregchess.bukkit.registerEvents
import gregc.gregchess.bukkit.renderer.ResetPlayerEvent
import gregc.gregchess.bukkit.renderer.renderer
import gregc.gregchess.match.ChessMatch
import gregc.gregchess.results.*
import org.bukkit.Bukkit
import org.bukkit.block.BlockFace
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.*
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
    fun onPlayerLeave(e: PlayerQuitEvent) = e.gregchessPlayer.leaveMatch()

    @EventHandler
    fun onPlayerDeath(e: EntityDeathEvent) {
        val ent = e.gregchessPlayer ?: return
        val match = ent.currentMatch ?: return
        match.callEvent(ResetPlayerEvent(ent))
    }

    @EventHandler
    fun onPlayerDamage(e: EntityDamageEvent) {
        val ent = e.gregchessPlayer ?: return
        val match = ent.currentMatch ?: return
        match.callEvent(ResetPlayerEvent(ent))
        e.isCancelled = true
    }

    @EventHandler
    fun onBlockClick(e: PlayerInteractEvent) {
        val player = e.gregchessPlayer.currentSide ?: return
        e.isCancelled = true
        if (player.hasTurn && e.blockFace != BlockFace.DOWN) {
            val block = e.clickedBlock ?: return
            val pos = player.match.renderer.getPos(block.location)
            if (e.action == Action.LEFT_CLICK_BLOCK && player.held == null) {
                player.pickUp(pos)
            } else if (e.action == Action.RIGHT_CLICK_BLOCK && player.held != null) {
                player.makeMove(pos)
            }
        }
    }

    @EventHandler
    fun onBlockBreak(e: BlockBreakEvent) {
        if (e.gregchessPlayer.isInMatch) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryDrag(e: InventoryDragEvent) {
        if (e.gregchessPlayer?.isInMatch == true) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryClick(e: InventoryClickEvent) {
        if (e.gregchessPlayer?.isInMatch == true) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onItemDrop(e: PlayerDropItemEvent) {
        if (e.gregchessPlayer.isInMatch)
            e.isCancelled = true
    }

    internal operator fun plusAssign(match: ChessMatch) {
        require(match.uuid !in matches)
        matches[match.uuid] = match
    }

    internal operator fun minusAssign(g: ChessMatch) {
        matches.remove(g.uuid, g)
    }

}
