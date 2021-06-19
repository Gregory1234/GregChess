package gregc.gregchess.chess

import gregc.gregchess.*
import gregc.gregchess.chess.component.GameEndEvent
import gregc.gregchess.chess.component.GameStartEvent
import org.bukkit.Bukkit
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
import org.bukkit.plugin.Plugin
import java.util.*

interface ChessGameManager {
    fun firstGame(predicate: (ChessGame) -> Boolean): ChessGame?
    operator fun get(uuid: UUID): ChessGame?
    fun leave(player: HumanPlayer)
}

class PluginRestartEndReason : EndReason(EndReasonConfig::pluginRestart, "emergency", quick = true)

class BukkitChessGameManager(private val plugin: Plugin) : ChessGameManager, Listener {

    private val games = mutableListOf<ChessGame>()

    override fun firstGame(predicate: (ChessGame) -> Boolean): ChessGame? = games.firstOrNull(predicate)

    private fun removeGame(g: ChessGame) {
        games -= g
        g.players.forEachReal { p ->
            p.games -= g
            p.currentGame = null
        }
    }

    override operator fun get(uuid: UUID): ChessGame? = games.firstOrNull { it.uniqueId == uuid }


    fun start() {
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }

    fun stop() {
        games.forEach { it.quickStop(PluginRestartEndReason()) }
    }

    override fun leave(player: HumanPlayer) {
        val games = player.games
        cRequire(games.isNotEmpty() || player.isSpectating(), Config.error.youNotInGame)
        games.forEach { g ->
            g.stop(EndReason.Walkover(!g[player]!!.side), BySides { it != g[player]!!.side })
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
        game.resetPlayer(ent.human)
        e.isCancelled = true
    }

    @EventHandler
    fun onBlockClick(e: PlayerInteractEvent) {
        val player = e.player.human.chess ?: return
        if (!e.player.human.isInGame() || e.player.human.isAdmin)
            return
        e.isCancelled = true
        if (player.hasTurn && e.blockFace != BlockFace.DOWN) {
            val block = e.clickedBlock ?: return
            val pos = player.game.cRequireRenderer<Loc, Pos> { it.getPos(block.loc) }
            if (e.action == Action.LEFT_CLICK_BLOCK && player.held == null) {
                player.pickUp(pos)
            } else if (e.action == Action.RIGHT_CLICK_BLOCK && player.held != null) {
                player.makeMove(pos)
            }
        }
    }

    @EventHandler
    fun onBlockBreak(e: BlockBreakEvent) {
        if (e.player.human.isInGame() && !e.player.human.isAdmin) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryDrag(e: InventoryDragEvent) {
        if (e.whoClicked.let { it is Player && it.human.isInGame() && !it.human.isAdmin }) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryClick(e: InventoryClickEvent) {
        if (e.whoClicked.let { it is Player && it.human.isInGame() && !it.human.isAdmin }) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onItemDrop(e: PlayerDropItemEvent) {
        if (e.player.human.isInGame() && !e.player.human.isAdmin) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onChessGameStart(e: GameStartEvent) {
        glog.low("Registering game", e.game.uniqueId)
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
