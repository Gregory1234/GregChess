package gregc.gregchess.chess

import gregc.gregchess.*
import org.bukkit.block.BlockFace
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
import java.lang.IllegalArgumentException
import java.util.*
import kotlin.contracts.ExperimentalContracts

class ChessManager(private val plugin: JavaPlugin) : Listener {
    private val games = mutableMapOf<UUID, ChessGame>()
    private val arenas = mutableListOf<ChessArena>()

    private fun nextArena(): ChessArena? = arenas.firstOrNull { it.isEmpty() }

    @ExperimentalContracts
    fun start() {
        plugin.server.pluginManager.registerEvents(this, plugin)
        arenas += ChessArena("arena1")

        plugin.addCommand("chess") { player, args ->
            commandRequireArgumentsMin(args, 1)
            when (args[0].toLowerCase()) {
                "duel" -> {
                    commandRequirePlayer(player)
                    commandRequireArguments(args, 2)
                    if (player.uniqueId in games)
                        throw CommandException("&cYou are in a game already!")
                    val opponent = GregChessInfo.server.getPlayer(args[1])
                    commandRequireNotNull(opponent, "&cPlayer doesn't exist!")
                    if (opponent.uniqueId in games)
                        throw CommandException("&cYour opponent is in a game already!")
                    val arena = nextArena()
                    commandRequireNotNull(arena, "&cThere are no free arenas!")
                    val game = ChessGame(player, opponent, arena)
                    game.start()
                    games[player.uniqueId] = game
                    games[opponent.uniqueId] = game
                }
                "leave" -> {
                    commandRequirePlayer(player)
                    commandRequireArguments(args, 1)
                    val game = games[player.uniqueId]
                    commandRequireNotNull(game, "&cYou are not in a game!")
                    game.stop(ChessGame.EndReason.Resignation(!game[player]!!.side), listOf(player))
                }
                "draw" -> {
                    commandRequirePlayer(player)
                    commandRequireArguments(args, 1)
                    val game = games[player.uniqueId]
                    commandRequireNotNull(game, "&cYou are not in a game!")
                    game[player]!!.let { it.wantsDraw = !it.wantsDraw }
                }
                "capture" -> {
                    commandRequirePlayer(player)
                    commandRequirePermission(player, "greg-chess.debug")
                    commandRequireArgumentsGeneral(args, 1, 2)
                    val game = games[player.uniqueId]
                    commandRequireNotNull(game, "&cYou are not in a game!")
                    if (args.size == 1) {
                        game.board[Loc.fromLocation(player.location)]?.capture()
                    } else {
                        try {
                            game.board[ChessPosition.parseFromString(args[1])]?.capture()
                        } catch (e: IllegalArgumentException) {
                            throw CommandException(e.toString())
                        }
                    }
                }
                "spawn" -> {
                    commandRequirePlayer(player)
                    commandRequirePermission(player, "greg-chess.debug")
                    commandRequireArgumentsGeneral(args, 3, 4)
                    val game = games[player.uniqueId]
                    commandRequireNotNull(game, "&cYou are not in a game!")
                    try {
                        if (args.size == 3) {
                            game.board[Loc.fromLocation(player.location)]?.capture()
                            game.board += ChessPiece(ChessPiece.Type.valueOf(args[2]), ChessSide.valueOf(args[1]), ChessPosition.fromLoc(Loc.fromLocation(player.location)), game)
                            game.board[Loc.fromLocation(player.location)]?.render()
                        } else {
                            game.board[ChessPosition.parseFromString(args[3])]?.capture()
                            game.board += ChessPiece(ChessPiece.Type.valueOf(args[2]), ChessSide.valueOf(args[1]), ChessPosition.parseFromString(args[3]), game)
                            game.board[ChessPosition.parseFromString(args[3])]!!.render()
                        }
                    } catch (e: Exception) {
                        throw CommandException(e.toString())
                    }
                }
                "move" -> {
                    commandRequirePlayer(player)
                    commandRequirePermission(player, "greg-chess.debug")
                    commandRequireArguments(args, 3)
                    val game = games[player.uniqueId]
                    commandRequireNotNull(game, "&cYou are not in a game!")
                    try {
                        game.board[ChessPosition.parseFromString(args[2])]?.capture()
                        game.board[ChessPosition.parseFromString(args[1])]?.pos = ChessPosition.parseFromString(args[2])
                    } catch (e: IllegalArgumentException) {
                        throw CommandException(e.toString())
                    }
                }
                "skip" -> {
                    commandRequirePlayer(player)
                    commandRequirePermission(player, "greg-chess.debug")
                    commandRequireArguments(args, 1)
                    val game = games[player.uniqueId]
                    commandRequireNotNull(game, "&cYou are not in a game!")
                    game.nextTurn()
                }
                else -> throw CommandException(GregChessInfo.WRONG_ARGUMENT)
            }
        }
        plugin.addCommandTab("chess") { s, args ->
            when (args.size) {
                1 -> (listOf("duel", "leave", "draw") + if (s.hasPermission("greg-chess.debug")) listOf("capture", "spawn", "move", "skip") else listOf()).filter { it.startsWith(args[0]) }
                2 -> when (args[0]) {
                    "duel" -> null
                    "leave" -> listOf()
                    "draw" -> listOf()
                    "capture" -> listOf()
                    "spawn" -> if (s.hasPermission("greg-chess.debug")) ChessSide.values().map { it.toString() }.filter { it.startsWith(args[1]) } else listOf()
                    "move" -> listOf()
                    "skip" -> listOf()
                    else -> listOf()
                }
                3 -> when (args[0]) {
                    "capture" -> listOf()
                    "spawn" -> if (s.hasPermission("greg-chess.debug")) ChessPiece.Type.values().map { it.toString() }.filter { it.startsWith(args[2]) } else listOf()
                    "move" -> listOf()
                    else -> listOf()
                }
                else -> listOf()
            }
        }
    }

    fun stop() {
        games.values.forEach { it.stop(ChessGame.EndReason.PluginRestart(), it.realPlayers) }
        arenas.forEach { it.delete() }
    }

    @EventHandler
    fun onPlayerLeave(e: PlayerQuitEvent) {
        if (e.player.uniqueId in games) {
            val game = games[e.player.uniqueId]!!
            game.stop(ChessGame.EndReason.Resignation(!game[e.player]!!.side), listOf(e.player))
        }
    }

    @EventHandler
    fun onPlayerDamage(e: EntityDamageEvent) {
        val ent = e.entity
        if (ent is Player && ent.uniqueId in games) {
            val game = games[ent.uniqueId]!!
            ent.health = 20.0
            ent.foodLevel = 20
            ent.teleport(game.arena.world.spawnLocation)
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onBlockClick(e: PlayerInteractEvent) {
        if (e.player.uniqueId in games) {
            e.isCancelled = true
            val player = games[e.player.uniqueId]!![e.player]!!
            val block = e.clickedBlock ?: return

            if (e.action == Action.LEFT_CLICK_BLOCK && player.held == null && player.hasTurn() && e.blockFace != BlockFace.DOWN) {
                player.pickUp(block.loc)
            } else if (e.action == Action.RIGHT_CLICK_BLOCK && player.held != null && player.hasTurn() && e.blockFace != BlockFace.DOWN) {
                player.makeMove(block.loc)
            }
        }
    }

    @EventHandler
    fun onBlockBreak(e: BlockBreakEvent) {
        if (e.player.uniqueId in games) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryDrag(e: InventoryDragEvent) {
        if (e.whoClicked.uniqueId in games) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryClick(e: InventoryClickEvent) {
        val uuid = e.whoClicked.uniqueId
        if (uuid in games) {
            e.isCancelled = true
            val holder = e.inventory.holder
            if (holder is ChessGame.PawnPromotionScreen) {
                e.currentItem?.let { holder.applyEvent(it.type); e.whoClicked.closeInventory() }
            }
        }
    }

    @EventHandler
    fun onInventoryClose(e: InventoryCloseEvent) {
        val uuid = e.player.uniqueId
        if (uuid in games) {
            val holder = e.inventory.holder
            if (holder is ChessGame.PawnPromotionScreen) {
                if (!holder.finished)
                    holder.applyEvent(null)
            }
        }
    }

    @EventHandler
    fun onWeatherChange(e: WeatherChangeEvent) {
        if (e.toWeatherState()) {
            if (e.world.name in arenas.map { it.world.name }) {
                e.isCancelled = true
            }
        }
    }

    @EventHandler
    fun onItemDrop(e: PlayerDropItemEvent) {
        if (e.player.uniqueId in games) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onChessGameEnd(e: ChessGame.EndEvent) {
        e.game.realPlayers.forEach { games.remove(it.uniqueId) }
    }
}
