package gregc.gregchess.chess

import gregc.gregchess.*
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
import org.bukkit.block.BlockFace
import org.bukkit.entity.HumanEntity
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

    private val playerGames: MutableMap<UUID, List<UUID>> = mutableMapOf()
    private val playerCurrentGames: MutableMap<UUID, UUID> = mutableMapOf()
    private val spectatorGames: MutableMap<UUID, UUID> = mutableMapOf()
    private val games: MutableMap<UUID, ChessGame> = mutableMapOf()

    private fun forEachGame(function: (ChessGame) -> Unit) = games.values.forEach(function)

    fun firstGame(function: (ChessGame) -> Boolean): ChessGame? = games.values.firstOrNull(function)

    private fun removeGame(g: ChessGame) {
        games.remove(g.uniqueId)
        g.forEachPlayer { p ->
            playerGames[p.uniqueId].orEmpty().filter { it != g.uniqueId }.let {
                if (it.isEmpty())
                    playerGames.remove(p.uniqueId)
                else
                    playerGames[p.uniqueId] = it
            }
            playerCurrentGames.remove(p.uniqueId)
        }
    }

    fun isInGame(p: HumanEntity) = p.uniqueId in playerGames

    fun getGame(p: Player) = games[playerCurrentGames[p.uniqueId]]
    fun getGames(p: Player) = playerGames[p.uniqueId].orEmpty().mapNotNull { games[it] }

    operator fun get(p: Player): BukkitChessPlayer? = getGame(p)?.get(p)

    operator fun get(uuid: UUID): ChessGame? = games[uuid]

    private fun isSpectatingGame(p: Player) = p.uniqueId in spectatorGames

    private fun getGameSpectator(p: Player) = games[spectatorGames[p.uniqueId]]

    private fun removeSpectator(p: Player) {
        val g = getGameSpectator(p) ?: return
        g.spectatorLeave(p)
        spectatorGames.remove(p.uniqueId)
    }

    private fun addSpectator(p: Player, g: ChessGame) {
        spectatorGames[p.uniqueId] = g.uniqueId
        g.spectate(p)
    }

    val arenas = mutableListOf<String>()

    fun start() {
        GregInfo.server.pluginManager.registerEvents(this, GregInfo.plugin)
        ConfigManager.getStringList("ChessArenas").forEach {
            arenas += it
        }
    }

    fun stop() {
        forEachGame { it.quickStop(ChessGame.EndReason.PluginRestart()) }
    }

    fun leave(player: Player) {
        val p = getGames(player)
        cRequire(p.isNotEmpty() || isSpectatingGame(player), "InGame.You")
        p.forEach {
            it.stop(ChessGame.EndReason.Walkover(!it[player]!!.side), listOf(player))
        }
        if (isSpectatingGame(player))
            removeSpectator(player)
    }

    fun addSpectator(player: Player, toSpectate: Player) {
        val spec = cNotNull(this[toSpectate], "NotInGame.Player")
        val game = getGame(player)
        if (game != null) {
            cRequire(player !in game, "InGame.You")
            removeSpectator(player)
        }
        addSpectator(player, spec.game)
    }

    fun reload() {
        arenas.clear()
        arenas.addAll(ConfigManager.getStringList("ChessArenas"))
    }

    @EventHandler
    fun onPlayerLeave(e: PlayerQuitEvent) {
        val p = getGames(e.player)
        p.forEach {
            it.stop(ChessGame.EndReason.Walkover(!it[e.player]!!.side), listOf(e.player))
        }
        if (isSpectatingGame(e.player))
            removeSpectator(e.player)
    }

    @EventHandler
    fun onPlayerDamage(e: EntityDamageEvent) {
        val ent = e.entity as? Player ?: return
        val game = getGame(ent) ?: return
        ent.health = 20.0
        ent.foodLevel = 20
        ent.teleport(game.renderer.spawnLocation)
        e.isCancelled = true
    }

    @EventHandler
    fun onBlockClick(e: PlayerInteractEvent) {
        val player = this[e.player] ?: return
        if (player.isAdmin)
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
        if (this[e.player]?.isAdmin == false) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryDrag(e: InventoryDragEvent) {
        if (e.whoClicked.let { it is Player && this[it]?.isAdmin == false }) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryClick(e: InventoryClickEvent) {
        if (e.whoClicked.let { it is Player && this[it]?.isAdmin == false }) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onWeatherChange(e: WeatherChangeEvent) {
        if (e.toWeatherState()) {
            if (e.world.name in arenas) {
                e.isCancelled = true
            }
        }
    }

    @EventHandler
    fun onItemDrop(e: PlayerDropItemEvent) {
        if (this[e.player]?.isAdmin == false) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onChessGameStart(e: ChessGame.StartEvent) {
        glog.low("Registering game", e.game.uniqueId)
        games[e.game.uniqueId] = e.game
        e.game.forEachPlayer {
            glog.low("Registering game player", it.uniqueId)
            playerGames[it.uniqueId] = playerGames[it.uniqueId].orEmpty() + e.game.uniqueId
            playerCurrentGames[it.uniqueId] = e.game.uniqueId
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
        e.game.forEachPlayer { it.spigot().sendMessage(message) }
        removeGame(e.game)
    }

    @EventHandler
    fun onCreatureSpawn(e: CreatureSpawnEvent) {
        if (e.location.world?.name in arenas) {
            e.isCancelled = true
        }
    }

    fun moveToGame(player: Player, newGame: ChessGame) {
        if (newGame.uniqueId !in playerGames[player.uniqueId].orEmpty())
            throw IllegalStateException("Player is not in that game")
        playerCurrentGames[player.uniqueId] = newGame.uniqueId
    }

    fun nextArena(): String? = arenas.firstOrNull {
        val w = Bukkit.getWorld(it)
        w == null || w.players.isEmpty()
    }

}
