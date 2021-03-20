package gregc.gregchess.chess

import gregc.gregchess.*
import gregc.gregchess.chess.component.ChessClock
import gregc.gregchess.chess.component.Chessboard
import org.bukkit.block.BlockFace
import org.bukkit.entity.HumanEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.weather.WeatherChangeEvent
import java.util.*
import kotlin.contracts.ExperimentalContracts
import org.bukkit.event.entity.CreatureSpawnEvent


object ChessManager : Listener {

    private val playerGames: MutableMap<UUID, UUID> = mutableMapOf()
    private val spectatorGames: MutableMap<UUID, UUID> = mutableMapOf()
    private val games: MutableMap<UUID, ChessGame> = mutableMapOf()

    private fun forEachGame(function: (ChessGame) -> Unit) = games.values.forEach(function)

    fun firstGame(function: (ChessGame) -> Boolean): ChessGame? = games.values.firstOrNull(function)

    private fun addGame(g: ChessGame) {
        games[g.uniqueId] = g
        g.forEachPlayer { playerGames[it.uniqueId] = g.uniqueId }
    }

    fun startGame(g: ChessGame) {
        addGame(g)
        g.start()
    }

    private fun removeGame(g: ChessGame) {
        games.remove(g.uniqueId)
        g.forEachPlayer { playerGames.remove(it.uniqueId) }
        g.forEachSpectator { spectatorGames.remove(it.uniqueId) }
    }

    fun isInGame(p: HumanEntity) = p.uniqueId in playerGames

    fun getGame(p: Player) = games[playerGames[p.uniqueId]]

    private fun getChessPlayer(p: Player) = getGame(p)?.get(p)
    operator fun get(p: Player): ChessPlayer.Human? = getChessPlayer(p)

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

    private val arenas = mutableListOf<ChessArena>()

    private fun nextArena(): ChessArena? = arenas.firstOrNull { it.isAvailable() }

    fun start() {
        GregInfo.server.pluginManager.registerEvents(this, GregInfo.plugin)
        ConfigManager.getStringList("ChessArenas").forEach {
            arenas += ChessArena(it)
        }
        Chessboard.Settings.init()
        ChessClock.Settings.init()
    }

    fun stop() {
        forEachGame { it.quickStop(ChessGame.EndReason.PluginRestart()) }
        arenas.forEach { it.delete() }
    }

    private fun reloadArenas() {
        val oldArenas = arenas.map { it.name }
        val newArenas = ConfigManager.getStringList("ChessArenas")
        val removedArenas = oldArenas - newArenas
        val addedArenas = newArenas - oldArenas
        removedArenas.forEach { name ->
            val arena = arenas.first { it.name == name }
            forEachGame {
                if (it.arena == arena) {
                    it.quickStop(ChessGame.EndReason.ArenaRemoved())
                }
            }
            arena.delete()
            arenas.remove(arena)
        }
        addedArenas.forEach { name ->
            arenas += ChessArena(name)
        }
    }

    @ExperimentalContracts
    fun duelMenu(player: Player, callback: (ChessArena, ChessGame.Settings) -> Unit) {
        val arena = nextArena()
        commandRequireNotNull(arena, "NoArenas")
        arena.reserve()
        player.openScreen(ChessGame.SettingsScreen(arena, callback))
    }

    fun leave(player: Player) {
        val p = getChessPlayer(player)
        if (p != null) {
            p.game.stop(ChessGame.EndReason.Walkover(!p.side), listOf(player))
        } else {
            if (!isSpectatingGame(player))
                throw CommandException("NotInGame.You")
            removeSpectator(player)
        }
    }

    @ExperimentalContracts
    fun addSpectator(player: Player, toSpectate: Player) {
        val spec = getChessPlayer(toSpectate)
        commandRequireNotNull(spec, "NotInGame.Player")
        val game = getGame(player)
        if (game != null) {
            if (player in game)
                throw CommandException("InGame.You")
            removeSpectator(player)
        }
        addSpectator(player, spec.game)
    }

    fun reload() {
        reloadArenas()
        Chessboard.Settings.init()
        ChessClock.Settings.init()
    }

    @EventHandler
    fun onPlayerLeave(e: PlayerQuitEvent) {
        val player = getChessPlayer(e.player)
        if (player != null)
            player.game.stop(ChessGame.EndReason.Resignation(!player.side), listOf(e.player))
        else if (isSpectatingGame(e.player))
            removeSpectator(e.player)
    }

    @EventHandler
    fun onPlayerDamage(e: EntityDamageEvent) {
        val ent = e.entity as? Player ?: return
        val game = getGame(ent) ?: return
        ent.health = 20.0
        ent.foodLevel = 20
        ent.teleport(game.world.spawnLocation)
        e.isCancelled = true
    }

    @EventHandler
    fun onBlockClick(e: PlayerInteractEvent) {
        val player = getChessPlayer(e.player) ?: return
        e.isCancelled = true
        val block = e.clickedBlock ?: return

        if (e.action == Action.LEFT_CLICK_BLOCK && player.held == null && player.hasTurn() && e.blockFace != BlockFace.DOWN) {
            player.pickUp(block.loc)
        } else if (e.action == Action.RIGHT_CLICK_BLOCK && player.held != null && player.hasTurn() && e.blockFace != BlockFace.DOWN) {
            player.makeMove(block.loc)
        }
    }

    @EventHandler
    fun onBlockBreak(e: BlockBreakEvent) {
        if (isInGame(e.player)) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryDrag(e: InventoryDragEvent) {
        if (isInGame(e.whoClicked)) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryClick(e: InventoryClickEvent) {
        if (isInGame(e.whoClicked)) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onWeatherChange(e: WeatherChangeEvent) {
        if (e.toWeatherState()) {
            if (e.world.name in arenas.map { it.name }) {
                e.isCancelled = true
            }
        }
    }

    @EventHandler
    fun onItemDrop(e: PlayerDropItemEvent) {
        if (isInGame(e.player)) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onChessGameEnd(e: ChessGame.EndEvent) {
        removeGame(e.game)
    }

    @EventHandler
    fun onCreatureSpawn(e: CreatureSpawnEvent) {
        if (e.location.world?.name in arenas.map { it.name }) {
            e.isCancelled = true
        }
    }

}
