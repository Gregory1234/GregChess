package gregc.gregchess.chess

import gregc.gregchess.*
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.World
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.weather.WeatherChangeEvent
import java.util.*


object ChessManager : Listener {

    private val games = mutableListOf<ChessGame>()

    private fun forEachGame(function: (ChessGame) -> Unit) = games.forEach(function)

    fun firstGame(function: (ChessGame) -> Boolean): ChessGame? = games.firstOrNull(function)

    fun registerArena(game: ChessGame) {
        arenas[game.arena] = game
    }

    fun expireGame(game: ChessGame) {
        arenas[game.arena] = null
    }

    private fun removeGame(g: ChessGame) {
        games -= g
        arenas[g.arena] = null
        g.players.forEachReal { p ->
            p.games -= g
            p.currentGame = null
        }
    }

    operator fun get(p: HumanPlayer): HumanChessPlayer? = p.currentGame?.get(p)

    operator fun get(uuid: UUID): ChessGame? = games.firstOrNull { it.uniqueId == uuid }

    private fun removeSpectator(p: HumanPlayer) {
        val g = p.spectatedGame ?: return
        g.spectatorLeave(p)
        p.spectatedGame = null
    }

    private fun addSpectator(p: HumanPlayer, g: ChessGame) {
        p.spectatedGame = g
        g.spectate(p)
    }

    private val arenas = mutableMapOf<Arena, ChessGame?>()

    private fun World.isArena(): Boolean = arenas.any {it.key.name == name}

    fun start() {
        GregInfo.server.pluginManager.registerEvents(this, GregInfo.plugin)
        arenas.putAll(ConfigManager.getStringList("ChessArenas").associate { Arena(it) to null })
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
        if (player.isSpectating())
            removeSpectator(player)
    }

    fun addSpectator(player: HumanPlayer, toSpectate: HumanPlayer) {
        val spec = cNotNull(this[toSpectate], "NotInGame.Player")
        val game = player.currentGame
        if (game != null) {
            cRequire(player !in game, "InGame.You")
            removeSpectator(player)
        }
        addSpectator(player, spec.game)
    }

    fun reload() {
        val newArenas = ConfigManager.getStringList("ChessArenas")
        arenas.forEach { (arena, game) ->
            if (arena.name in newArenas){
                game?.quickStop(ChessGame.EndReason.ArenaRemoved())
                arenas.remove(arena)
            }
        }
        arenas.putAll((newArenas-arenas.map {it.key.name}).associate { Arena(it) to null })
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
        if (e.player.human.isSpectating())
            removeSpectator(e.player.human)
    }

    @EventHandler
    fun onPlayerDamage(e: EntityDamageEvent) {
        val ent = e.entity as? Player ?: return
        val game = ent.human.currentGame ?: return
        game.renderer.resetPlayer(ent.human)
        e.isCancelled = true
    }

    @EventHandler
    fun onBlockClick(e: PlayerInteractEvent) {
        val player = this[e.player.human] ?: return
        if (!e.player.human.isInGame() || e.player.human.isAdmin)
            return
        e.isCancelled = true
        if (player.hasTurn && e.blockFace != BlockFace.DOWN) {
            val block = e.clickedBlock ?: return
            if (e.action == Action.LEFT_CLICK_BLOCK && player.held == null) {
                player.pickUp(player.game.renderer.getPos(block.loc))
            } else if (e.action == Action.RIGHT_CLICK_BLOCK && player.held != null) {
                player.makeMove(player.game.renderer.getPos(block.loc))
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
    fun onWeatherChange(e: WeatherChangeEvent) {
        if (e.toWeatherState()) {
            if (e.world.isArena()) {
                e.isCancelled = true
            }
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
            glog.low("Registering game player", it.bukkit.uniqueId)
            it.games += e.game
            it.currentGame = e.game
        }
    }

    @EventHandler
    fun onChessGameEnd(e: ChessGame.EndEvent) {
        val message = TextComponent(ConfigManager.getString("Message.CopyPGN"))
        message.clickEvent =
            ClickEvent(
                ClickEvent.Action.COPY_TO_CLIPBOARD,
                PGN.generate(e.game).toString()
            )
        e.game.players.forEachReal { it.bukkit.spigot().sendMessage(message) }
        removeGame(e.game)
    }

    @EventHandler
    fun onCreatureSpawn(e: CreatureSpawnEvent) {
        if (e.location.world?.isArena() == true) {
            e.isCancelled = true
        }
    }

    fun nextArena(): Arena? = arenas.toList().firstOrNull { (_, game) -> game == null }?.first

    fun cNextArena() = cNotNull(nextArena(), "NoArenas")

}
