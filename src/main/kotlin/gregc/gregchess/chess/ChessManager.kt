package gregc.gregchess.chess

import gregc.gregchess.*
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
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.event.entity.CreatureSpawnEvent
import java.util.concurrent.TimeUnit


class ChessManager(private val plugin: JavaPlugin) : Listener {
    private class PlayerMap {
        private val games: MutableMap<UUID, ChessGame> = mutableMapOf()
        private val gameList: MutableList<ChessGame> = mutableListOf()

        operator fun get(player: Player) = games[player.uniqueId]?.get(player)
        operator fun plusAssign(game: ChessGame) {
            game.realPlayers.forEach { games[it.uniqueId] = game }
            gameList += game
        }

        fun remove(game: ChessGame) {
            game.realPlayers.forEach { games.remove(it.uniqueId) }
            gameList.remove(game)
        }

        operator fun contains(player: HumanEntity) = player.uniqueId in games
        fun forEachGame(f: (ChessGame) -> Unit) {
            gameList.forEach(f)
        }
    }

    private val players = PlayerMap()
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
                    if (player in players)
                        throw CommandException("&cYou are in a game already!")
                    val opponent = GregChessInfo.server.getPlayer(args[1])
                    commandRequireNotNull(opponent, "&cPlayer doesn't exist!")
                    if (opponent in players)
                        throw CommandException("&cYour opponent is in a game already!")
                    val arena = nextArena()
                    commandRequireNotNull(arena, "&cThere are no free arenas!")
                    val game = ChessGame(player, opponent, arena, GameSettings.rapid10)
                    game.start()
                    players += game
                }
                "leave" -> {
                    commandRequirePlayer(player)
                    commandRequireArguments(args, 1)
                    val p = players[player]
                    commandRequireNotNull(p, "&cYou are not in a game!")
                    p.game.stop(ChessGame.EndReason.Resignation(!p.side), listOf(player))
                }
                "draw" -> {
                    commandRequirePlayer(player)
                    commandRequireArguments(args, 1)
                    val p = players[player]
                    commandRequireNotNull(p, "&cYou are not in a game!")
                    p.wantsDraw = !p.wantsDraw
                }
                "capture" -> {
                    commandRequirePlayer(player)
                    commandRequirePermission(player, "greg-chess.debug")
                    commandRequireArgumentsGeneral(args, 1, 2)
                    val p = players[player]
                    commandRequireNotNull(p, "&cYou are not in a game!")
                    val pos = if (args.size == 1) {
                        ChessPosition.fromLoc(Loc.fromLocation(player.location))
                    } else {
                        try {
                            ChessPosition.parseFromString(args[1])
                        } catch (e: IllegalArgumentException) {
                            throw CommandException(e.toString())
                        }
                    }
                    p.game.board.capture(pos)
                    p.game.board.updateMoves()
                }
                "spawn" -> {
                    commandRequirePlayer(player)
                    commandRequirePermission(player, "greg-chess.debug")
                    commandRequireArgumentsGeneral(args, 3, 4)
                    val game = players[player]?.game
                    commandRequireNotNull(game, "&cYou are not in a game!")
                    try {
                        val pos = if (args.size == 3)
                            ChessPosition.fromLoc(Loc.fromLocation(player.location))
                        else
                            ChessPosition.parseFromString(args[3])
                        val piece = ChessPiece.Type.valueOf(args[2])

                        game.board.capture(pos)
                        game.board += ChessPiece(piece, ChessSide.valueOf(args[1]), pos, false)
                        game.board.updateMoves()
                    } catch (e: Exception) {
                        throw CommandException(e.toString())
                    }
                }
                "move" -> {
                    commandRequirePlayer(player)
                    commandRequirePermission(player, "greg-chess.debug")
                    commandRequireArguments(args, 3)
                    val game = players[player]?.game
                    commandRequireNotNull(game, "&cYou are not in a game!")
                    try {
                        game.board.capture(ChessPosition.parseFromString(args[2]))
                        game.board.move(ChessPosition.parseFromString(args[1]), ChessPosition.parseFromString(args[2]))
                        game.board.updateMoves()
                    } catch (e: IllegalArgumentException) {
                        throw CommandException(e.toString())
                    }
                }
                "skip" -> {
                    commandRequirePlayer(player)
                    commandRequirePermission(player, "greg-chess.debug")
                    commandRequireArguments(args, 1)
                    val game = players[player]?.game
                    commandRequireNotNull(game, "&cYou are not in a game!")
                    game.nextTurn()
                }
                "load" -> {
                    commandRequirePlayer(player)
                    commandRequirePermission(player, "greg-chess.debug")
                    val game = players[player]?.game
                    commandRequireNotNull(game, "&cYou are not in a game!")
                    game.board.setFromFEN(args.drop(1).joinToString(separator = " "))
                }
                "save" -> {
                    commandRequirePlayer(player)
                    val game = players[player]?.game
                    commandRequireNotNull(game, "&cYou are not in a game!")
                    val message = TextComponent("Copy FEN")
                    message.clickEvent = ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, game.board.getFEN())
                    player.spigot().sendMessage(message)
                }
                "timeadd" -> {
                    commandRequirePlayer(player)
                    commandRequirePermission(player, "greg-chess.debug")
                    commandRequireArguments(args, 3)
                    val game = players[player]?.game
                    commandRequireNotNull(game, "&cYou are not in a game!")
                    try {
                        game.timer.addTime(ChessSide.valueOf(args[1]),TimeUnit.SECONDS.toMillis(args[2].toLong()))
                    } catch (e: IllegalArgumentException) {
                        throw CommandException(e.toString())
                    }
                }
                else -> throw CommandException(GregChessInfo.WRONG_ARGUMENT)
            }
        }
        plugin.addCommandTab("chess") { s, args ->
            when (args.size) {
                1 -> (listOf("duel", "leave", "draw", "save") +
                        if (s.hasPermission("greg-chess.debug"))
                            listOf("capture", "spawn", "move", "skip", "load", "timeadd")
                        else
                            listOf()
                        ).filter { it.startsWith(args[0]) }
                2 -> when (args[0]) {
                    "duel" -> null
                    "spawn" -> if (s.hasPermission("greg-chess.debug"))
                        ChessSide.values().map { it.toString() }.filter { it.startsWith(args[1]) }
                    else
                        listOf()
                    "timeadd" -> if (s.hasPermission("greg-chess.debug"))
                        ChessSide.values().map { it.toString() }.filter { it.startsWith(args[1]) }
                    else
                        listOf()
                    else -> listOf()
                }
                3 -> when (args[0]) {
                    "spawn" -> if (s.hasPermission("greg-chess.debug"))
                        ChessPiece.Type.values().map { it.toString() }.filter { it.startsWith(args[2]) }
                    else
                        listOf()
                    else -> listOf()
                }
                else -> listOf()
            }
        }
    }

    fun stop() {
        players.forEachGame { it.stop(ChessGame.EndReason.PluginRestart(), it.realPlayers) }
        arenas.forEach { it.delete() }
    }

    @EventHandler
    fun onPlayerLeave(e: PlayerQuitEvent) {
        if (e.player in players) {
            val player = players[e.player]!!
            player.game.stop(ChessGame.EndReason.Resignation(!player.side), listOf(e.player))
        }
    }

    @EventHandler
    fun onPlayerDamage(e: EntityDamageEvent) {
        val ent = e.entity
        if (ent is Player && ent in players) {
            val game = players[ent]!!.game
            ent.health = 20.0
            ent.foodLevel = 20
            ent.teleport(game.world.spawnLocation)
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onBlockClick(e: PlayerInteractEvent) {
        if (e.player in players) {
            e.isCancelled = true
            val player = players[e.player]!!
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
        if (e.whoClicked in players) {
            e.isCancelled = true
            val holder = e.inventory.holder
            if (holder is ChessPlayer.PawnPromotionScreen) {
                e.currentItem?.let { holder.applyEvent(it.type); e.whoClicked.closeInventory() }
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
