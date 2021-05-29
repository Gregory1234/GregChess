package gregc.gregchess.chess

import gregc.gregchess.*
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.*
import org.bukkit.event.block.*
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.inventory.*
import org.bukkit.event.player.*
import java.util.*


object ChessManager : Listener {

    private val games = mutableListOf<ChessGame>()

    private fun forEachGame(function: (ChessGame) -> Unit) = games.forEach(function)

    fun firstGame(function: (ChessGame) -> Boolean): ChessGame? = games.firstOrNull(function)

    private fun removeGame(g: ChessGame) {
        games -= g
        g.players.forEachReal { p ->
            p.games -= g
            p.currentGame = null
        }
    }

    operator fun get(uuid: UUID): ChessGame? = games.firstOrNull { it.uniqueId == uuid }


    fun start() {
        GregInfo.server.pluginManager.registerEvents(this, GregInfo.plugin)
    }

    fun stop() {
        forEachGame { it.quickStop(ChessGame.EndReason.PluginRestart()) }
    }

    fun leave(player: HumanPlayer) {
        val p = player.games
        cRequire(p.isNotEmpty() || player.isSpectating(), "InGame.You")
        p.forEach {
            it.stop(
                ChessGame.EndReason.Walkover(!it[player]!!.side),
                BySides(Unit, Unit).mapIndexed { side, _ -> side != it[player]!!.side }
            )
        }
        player.spectatedGame = null
    }

    @EventHandler
    fun onPlayerLeave(e: PlayerQuitEvent) {
        val p = e.player.human.games
        p.forEach {
            it.stop(
                ChessGame.EndReason.Walkover(!it[e.player.human]!!.side),
                BySides(Unit, Unit).mapIndexed { side, _ -> side != it[e.player.human]!!.side }
            )
        }
        e.player.human.spectatedGame = null
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
            val pos = cNotNull(player.game.withRenderer<Loc, Pos> { it.getPos(block.loc) }, "RendererNotFound")
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
        e.game.players.forEachReal { it.sendPGN(pgn) }
        removeGame(e.game)
    }

}
