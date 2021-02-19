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
import org.bukkit.plugin.java.JavaPlugin
import java.util.*
import kotlin.contracts.ExperimentalContracts
import org.bukkit.event.entity.CreatureSpawnEvent


class ChessManager(private val plugin: JavaPlugin, val timeManager: TimeManager) : Listener {
    private class PlayerMap {
        private val games: MutableMap<UUID, ChessGame> = mutableMapOf()
        private val spectators: MutableMap<UUID, ChessGame> = mutableMapOf()
        private val gameList: MutableList<ChessGame> = mutableListOf()

        operator fun get(player: Player) = games[player.uniqueId]?.get(player)
        operator fun plusAssign(game: ChessGame) {
            game.realPlayers.forEach { games[it.uniqueId] = game }
            gameList += game
        }
        fun getGame(player: Player) = games[player.uniqueId] ?: spectators[player.uniqueId]

        fun remove(game: ChessGame) {
            game.realPlayers.forEach { games.remove(it.uniqueId) }
            game.spectators.forEach { spectators.remove(it.uniqueId) }
            gameList.remove(game)
        }

        operator fun contains(player: HumanEntity) = player.uniqueId in games || player.uniqueId in spectators
        fun forEachGame(f: (ChessGame) -> Unit) {
            gameList.forEach(f)
        }

        fun stopSpectating(player: Player) {
            spectators[player.uniqueId]?.spectatorLeave(player)
            spectators.remove(player.uniqueId)
        }

        fun spectate(player: Player, game: ChessGame) {
            game.spectate(player)
            spectators[player.uniqueId] = game
        }
    }

    val settingsManager = SettingsManager(plugin)

    private val players = PlayerMap()
    private val arenas = mutableListOf<ChessArena>()

    private fun nextArena(): ChessArena? = arenas.firstOrNull { it.isEmpty() }

    fun start() {
        plugin.server.pluginManager.registerEvents(this, plugin)
        plugin.config.getStringList("ChessArenas").forEach {
            arenas += ChessArena(it)
        }
        Chessboard.Settings.init(settingsManager)
        ChessClock.Settings.init(settingsManager)
    }

    fun stop() {
        players.forEachGame { it.stop(ChessGame.EndReason.PluginRestart(), it.realPlayers) }
        arenas.forEach { it.delete() }
    }

    private fun reloadArenas(){
        val oldArenas = arenas.map {it.name}
        val newArenas = plugin.config.getStringList("ChessArenas")
        val removedArenas = oldArenas - newArenas
        val addedArenas = newArenas - oldArenas
        removedArenas.forEach { name ->
            val arena = arenas.first { it.name == name }
            players.forEachGame {
                if (it.arena == arena) {
                    it.stop(ChessGame.EndReason.ArenaRemoved(), it.realPlayers)
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
    fun duelMenu(player: Player, opponent: Player, callback: (ChessArena, ChessGame.Settings) -> Unit) {
        if (player in players)
            throw CommandException(string("Message.Error.InGame.You"))
        if (opponent in players)
            throw CommandException(string("Message.Error.InGame.Opponent"))
        val arena = nextArena()
        commandRequireNotNull(arena, string("Message.Error.NoArenas"))
        player.openInventory(ChessGame.SettingsMenu(settingsManager) {
            callback(arena, it)
        }.inventory)
    }

    fun startDuel(player: Player, opponent: Player, arena: ChessArena, settings: ChessGame.Settings) {
        if (!arena.isEmpty())
            return
        val game = ChessGame(player, opponent, arena, settings, this)
        game.start()
        players += game
    }

    @ExperimentalContracts
    fun stockfish(player: Player) {
        if (player in players)
            throw CommandException(string("Message.Error.InGame.You"))
        val arena = nextArena()
        commandRequireNotNull(arena, string("Message.Error.NoArenas"))
        player.openInventory(ChessGame.SettingsMenu(settingsManager) {
            if (!arena.isEmpty())
                return@SettingsMenu
            val white = ChessPlayer.Human(player, ChessSide.WHITE, false)
            val black = ChessPlayer.Engine(ChessEngine("stockfish"), ChessSide.BLACK)
            val game = ChessGame(white, black, arena, it, this)
            game.start()
            players += game
        }.inventory)
    }

    operator fun get(player: Player) = players[player]

    fun getGame(player: Player) = players.getGame(player)

    fun leave(player: Player) {
        val p = players[player]
        if (p != null) {
            p.game.stop(ChessGame.EndReason.Walkover(!p.side), listOf(player))
        } else {
            if (player !in players)
                throw CommandException(string("Message.Error.NotInGame.You"))
            players.stopSpectating(player)
        }
    }

    @ExperimentalContracts
    fun spectate(player: Player, toSpectate: Player) {
        val spec = players[toSpectate]
        commandRequireNotNull(spec, string("Message.Error.NotInGame.Player"))
        val game = players.getGame(player)
        if (game != null) {
            if (player in game.realPlayers)
                throw CommandException(string("Message.Error.InGame.You"))
            players.stopSpectating(player)
        }
        players.spectate(player, spec.game)
    }

    fun reload() {
        reloadArenas()
        Chessboard.Settings.init(settingsManager)
        ChessClock.Settings.init(settingsManager)
    }

    @EventHandler
    fun onPlayerLeave(e: PlayerQuitEvent) {
        if (e.player in players) {
            val player = players[e.player]
            if (player != null)
                player.game.stop(ChessGame.EndReason.Resignation(!player.side), listOf(e.player))
            else
                players.stopSpectating(e.player)
        }
    }

    @EventHandler
    fun onPlayerDamage(e: EntityDamageEvent) {
        val ent = e.entity as? Player ?: return
        val game = players.getGame(ent) ?: return
        ent.health = 20.0
        ent.foodLevel = 20
        ent.teleport(game.world.spawnLocation)
        e.isCancelled = true
    }

    @EventHandler
    fun onBlockClick(e: PlayerInteractEvent) {
        val player = players[e.player] ?: return
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
        if (e.player in players) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryDrag(e: InventoryDragEvent) {
        if (e.whoClicked in players) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryClick(e: InventoryClickEvent) {
        val holder = e.inventory.holder
        if (e.whoClicked in players) {
            e.isCancelled = true
            if (holder is ChessPlayer.PawnPromotionScreen) {
                e.currentItem?.let { holder.applyEvent(it.type); e.whoClicked.closeInventory() }
            }
        } else {
            if (holder is ChessGame.SettingsMenu) {
                e.currentItem?.itemMeta?.displayName?.let { holder.applyEvent(it); e.whoClicked.closeInventory() }
                e.isCancelled = true
            }
        }
    }

    @EventHandler
    fun onInventoryClose(e: InventoryCloseEvent) {
        if (e.player in players) {
            val holder = e.inventory.holder
            if (holder is ChessPlayer.PawnPromotionScreen) {
                if (!holder.finished)
                    holder.applyEvent(null)
            }
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
        if (e.player in players) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onChessGameEnd(e: ChessGame.EndEvent) {
        players.remove(e.game)
    }

    @EventHandler
    fun onCreatureSpawn(e: CreatureSpawnEvent) {
        if (e.location.world?.name in arenas.map { it.name }) {
            e.isCancelled = true
        }
    }
}
