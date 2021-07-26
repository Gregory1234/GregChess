package gregc.gregchess.bukkit.chess

import gregc.gregchess.asIdent
import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.chess.component.GameEndEvent
import gregc.gregchess.bukkit.chess.component.GameStartEvent
import gregc.gregchess.chess.*
import gregc.gregchess.glog
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.*
import java.util.*

object ChessGameManager : Listener {

    private val PLUGIN_RESTART = DrawEndReason("plugin_restart".asIdent(), EndReason.Type.EMERGENCY, quick = true)

    private val games = mutableListOf<ChessGame>()

    fun firstGame(predicate: (ChessGame) -> Boolean): ChessGame? = games.firstOrNull(predicate)

    private fun removeGame(g: ChessGame) {
        games -= g
        g.players.forEachReal { p ->
            p.games -= g
            p.currentGame = null
        }
    }

    operator fun get(uuid: UUID): ChessGame? = games.firstOrNull { it.uuid == uuid }


    fun start() {
        registerEvents()
    }

    fun stop() {
        games.forEach { it.quickStop(drawBy(PLUGIN_RESTART)) }
    }

    fun leave(player: BukkitPlayer) {
        val games = player.games
        cRequire(games.isNotEmpty() || player.isSpectating, YOU_NOT_IN_GAME)
        games.forEach { g ->
            g.stop(g[player]!!.side.lostBy(EndReason.WALKOVER), BySides { it == g[player]!!.side })
        }
        player.spectatedGame = null
    }

    @EventHandler
    fun onPlayerLeave(e: PlayerQuitEvent) = try {
        leave(e.player.human)
    } catch (ex: CommandException) {

    }

    @EventHandler
    fun onPlayerDamage(e: EntityDamageEvent) {
        val ent = e.entity as? Player ?: return
        val game = ent.human.currentGame ?: return
        game.arenaUsage.resetPlayer(ent.human)
        e.isCancelled = true
    }

    @EventHandler
    fun onBlockClick(e: PlayerInteractEvent) {
        val player = e.player.human.chess ?: return
        if (!e.player.human.isInGame || e.player.human.isAdmin)
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
        if (e.player.human.isInGame && !e.player.human.isAdmin) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryDrag(e: InventoryDragEvent) {
        if (e.whoClicked.let { it is Player && it.human.isInGame && !it.human.isAdmin }) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryClick(e: InventoryClickEvent) {
        if (e.whoClicked.let { it is Player && it.human.isInGame && !it.human.isAdmin }) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onItemDrop(e: PlayerDropItemEvent) {
        if (e.player.human.isInGame && !e.player.human.isAdmin) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onChessGameStart(e: GameStartEvent) {
        glog.low("Registering game", e.game.uuid)
        games += e.game
        e.game.players.forEachReal {
            glog.low("Registering game player", it)
            it.games += e.game
            it.currentGame = e.game
        }
    }

    @EventHandler
    fun onChessGameEnd(e: GameEndEvent) = removeGame(e.game)

}
