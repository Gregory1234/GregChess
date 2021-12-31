package gregc.gregchess.bukkit.chess

import gregc.gregchess.GregChess
import gregc.gregchess.bukkit.chess.component.*
import gregc.gregchess.bukkit.chess.player.*
import gregc.gregchess.bukkit.loc
import gregc.gregchess.bukkit.registerEvents
import gregc.gregchess.chess.*
import gregc.gregchess.register
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
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

object ChessGameManager : Listener {
    @JvmField
    val PLUGIN_RESTART = GregChess.register("plugin_restart", DrawEndReason(EndReason.Type.EMERGENCY))

    private val games = mutableListOf<ChessGame>()

    operator fun get(uuid: UUID): ChessGame? = games.firstOrNull { it.uuid == uuid }


    fun start() {
        registerEvents()
    }

    fun stop() {
        for (g in games)
            g.quickStop(drawBy(PLUGIN_RESTART))
    }

    fun leave(player: Player) {
        val g = player.chess
        player.spectatedGame = null
        g?.game?.stop(g.color.lostBy(EndReason.WALKOVER), byColor { it == g.color })
    }

    @EventHandler
    fun onPlayerLeave(e: PlayerQuitEvent) = leave(e.player)

    @EventHandler
    fun onPlayerDeath(e: EntityDeathEvent) {
        val ent = e.entity as? Player ?: return
        val game = ent.currentGame ?: return
        game.callEvent(ResetPlayerEvent(ent))
    }

    @EventHandler
    fun onPlayerDamage(e: EntityDamageEvent) {
        val ent = e.entity as? Player ?: return
        ent.currentGame ?: return
        e.isCancelled = true
    }

    @EventHandler
    fun onBlockClick(e: PlayerInteractEvent) {
        val player = e.player.chess ?: return
        if (!e.player.isInGame || e.player.isAdmin)
            return
        e.isCancelled = true
        if (player.hasTurn && e.blockFace != BlockFace.DOWN) {
            val block = e.clickedBlock ?: return
            val pos = player.game.renderer.getPos(block.loc)
            if (e.action == Action.LEFT_CLICK_BLOCK && player.held == null) {
                player.pickUp(pos)
            } else if (e.action == Action.RIGHT_CLICK_BLOCK && player.held != null) {
                player.makeMove(pos)
            }
        }
    }

    @EventHandler
    fun onBlockBreak(e: BlockBreakEvent) {
        if (e.player.isInGame && !e.player.isAdmin) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryDrag(e: InventoryDragEvent) {
        if (e.whoClicked.let { it is Player && it.isInGame && !it.isAdmin }) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryClick(e: InventoryClickEvent) {
        if (e.whoClicked.let { it is Player && it.isInGame && !it.isAdmin }) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onItemDrop(e: PlayerDropItemEvent) {
        if (e.player.isInGame && !e.player.isAdmin) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onChessGameStart(e: GameStartEvent) {
        games += e.game
    }

    @EventHandler
    fun onChessGameEnd(e: GameEndEvent) {
        games -= e.game
    }

}
