package gregc.gregchess.chess

import gregc.gregchess.*
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
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin
import java.util.*

interface ChessGameManager {
    fun firstGame(predicate: (ChessGame) -> Boolean): ChessGame?
    operator fun get(uuid: UUID): ChessGame?
    fun leave(player: HumanPlayer)
}

class BukkitChessGameManager(private val plugin: Plugin, private val config: Configurator) : ChessGameManager, Listener {

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
        games.forEach { it.quickStop(ChessGame.EndReason.PluginRestart()) }
    }

    override fun leave(player: HumanPlayer) {
        val games = player.games
        cRequire(games.isNotEmpty() || player.isSpectating(), errorMsg.inGame.you)
        games.forEach { g ->
            g.stop(ChessGame.EndReason.Walkover(!g[player]!!.side), BySides{ it != g[player]!!.side })
        }
        player.spectatedGame = null
    }

    @EventHandler
    fun onPlayerLeave(e: PlayerQuitEvent) = leave(e.player.human)

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
            val pos = cNotNull(player.game.withRenderer<Loc, Pos> { it.getPos(block.loc) }, errorMsg.rendererNotFound)
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
    fun onChessGameStart(e: ChessGame.StartEvent) {
        glog.low("Registering game", e.game.uniqueId)
        games += e.game
        e.game.players.forEachReal {
            glog.low("Registering game player", it)
            it.games += e.game
            it.currentGame = e.game
        }
    }

    @EventHandler
    fun onChessGameEnd(e: ChessGame.EndEvent) {
        val pgn = PGN.generate(e.game)
        e.game.players.forEachReal { it.sendPGN(config, pgn) }
        removeGame(e.game)
    }

}
