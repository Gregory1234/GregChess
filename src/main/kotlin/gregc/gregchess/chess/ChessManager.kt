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

    private val players = PlayerMap()
    private val arenas = mutableListOf<ChessArena>()

    private fun nextArena(): ChessArena? = arenas.firstOrNull { it.isEmpty() }

    @ExperimentalContracts
    fun start() {
        plugin.server.pluginManager.registerEvents(this, plugin)
        plugin.config.getStringList("ChessArenas").forEach {
            arenas += ChessArena(it)
        }

        plugin.addCommand("chess") { player, args ->
            commandRequireArgumentsMin(args, 1)
            when (args[0].toLowerCase()) {
                "duel" -> {
                    commandRequirePlayer(player)
                    commandRequireArguments(args, 2)
                    if (player in players)
                        throw CommandException(string("Message.Error.InGame.You"))
                    val opponent = GregChessInfo.server.getPlayer(args[1])
                    commandRequireNotNull(opponent, string("Message.Error.PlayerNotFound"))
                    if (opponent in players)
                        throw CommandException(string("Message.Error.InGame.Opponent"))
                    val arena = nextArena()
                    commandRequireNotNull(arena, string("Message.Error.NoArenas"))
                    player.openInventory(ChessGame.SettingsMenu {
                        if (!arena.isEmpty())
                            return@SettingsMenu
                        val game = ChessGame(player, opponent, arena, it)
                        game.start()
                        players += game
                    }.inventory)
                }
                "stockfish" -> {
                    commandRequirePlayer(player)
                    commandRequireArguments(args, 1)
                    if (player in players)
                        throw CommandException(string("Message.Error.InGame.You"))
                    val arena = nextArena()
                    commandRequireNotNull(arena, string("Message.Error.NoArenas"))
                    player.openInventory(ChessGame.SettingsMenu {
                        if (!arena.isEmpty())
                            return@SettingsMenu
                        val white = ChessPlayer.Human(player, ChessSide.WHITE, false)
                        val black = ChessPlayer.Engine("stockfish", ChessSide.BLACK)
                        val game = ChessGame(white, black, arena, it)
                        game.start()
                        players += game
                    }.inventory)
                }
                "resign" -> {
                    commandRequirePlayer(player)
                    commandRequireArguments(args, 1)
                    val p = players[player]
                    commandRequireNotNull(p, string("Message.Error.NotInGame.You"))
                    p.game.stop(ChessGame.EndReason.Resignation(!p.side))
                }
                "leave" -> {
                    commandRequirePlayer(player)
                    commandRequireArguments(args, 1)
                    val p = players[player]
                    if (p != null) {
                        p.game.stop(ChessGame.EndReason.Walkover(!p.side), listOf(player))
                    } else {
                        if (player !in players)
                            throw CommandException(string("Message.Error.NotInGame.You"))
                        players.stopSpectating(player)
                    }

                }
                "draw" -> {
                    commandRequirePlayer(player)
                    commandRequireArguments(args, 1)
                    val p = players[player]
                    commandRequireNotNull(p, string("Message.Error.NotInGame.You"))
                    p.wantsDraw = !p.wantsDraw
                }
                "capture" -> {
                    commandRequirePlayer(player)
                    commandRequirePermission(player, "greg-chess.debug")
                    commandRequireArgumentsGeneral(args, 1, 2)
                    val p = players[player]
                    commandRequireNotNull(p, string("Message.Error.NotInGame.You"))
                    val pos = if (args.size == 1) {
                        p.game.board.getPos(Loc.fromLocation(player.location))
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
                    val game = players.getGame(player)
                    commandRequireNotNull(game, string("Message.Error.NotInGame.You"))
                    try {
                        val pos = if (args.size == 3)
                            game.board.getPos(Loc.fromLocation(player.location))
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
                    val game = players.getGame(player)
                    commandRequireNotNull(game, string("Message.Error.NotInGame.You"))
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
                    val game = players.getGame(player)
                    commandRequireNotNull(game, string("Message.Error.NotInGame.You"))
                    game.nextTurn()
                }
                "load" -> {
                    commandRequirePlayer(player)
                    commandRequirePermission(player, "greg-chess.debug")
                    val game = players.getGame(player)
                    commandRequireNotNull(game, string("Message.Error.NotInGame.You"))
                    game.board.setFromFEN(args.drop(1).joinToString(separator = " "))
                }
                "save" -> {
                    commandRequirePlayer(player)
                    val game = players.getGame(player)
                    commandRequireNotNull(game, string("Message.Error.NotInGame.You"))
                    val message = TextComponent(string("Message.CopyFEN"))
                    message.clickEvent = ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, game.board.getFEN())
                    player.spigot().sendMessage(message)
                }
                "time" -> {
                    commandRequirePlayer(player)
                    commandRequirePermission(player, "greg-chess.debug")
                    commandRequireArguments(args, 4)
                    val game = players.getGame(player)
                    commandRequireNotNull(game, string("Message.Error.NotInGame.You"))
                    val timer = game.getComponent(ChessTimer::class)
                    commandRequireNotNull(timer, string("Message.Error.TimerNotFound"))
                    try {
                        val side = ChessSide.valueOf(args[1])
                        val time = TimeUnit.SECONDS.toMillis(args[3].toLong())
                        when (args[2].toLowerCase()) {
                            "add" -> timer.addTime(side, time)
                            "set" -> timer.setTime(side, time)
                            else -> throw CommandException(string("wrong_argument"))
                        }
                    } catch (e: IllegalArgumentException) {
                        throw CommandException(e.toString())
                    }
                }
                "uci" -> {
                    commandRequirePlayer(player)
                    commandRequirePermission(player, "greg-chess.debug")
                    commandRequireArgumentsMin(args, 2)
                    val game = players.getGame(player)
                    commandRequireNotNull(game, string("Message.Error.NotInGame.You"))
                    val engine = game.players.mapNotNull { it as? ChessPlayer.Engine }.firstOrNull()
                    commandRequireNotNull(engine, string("Message.Error.EngineNotFound"))
                    try {
                        when (args[1].toLowerCase()) {
                            "set" -> {
                                commandRequireArgumentsMin(args, 3)
                                engine.setOption(args[2], args.drop(3).joinToString(" "))
                            }
                            "send" -> engine.sendCommand(args.drop(2).joinToString(" "))
                            else -> throw CommandException(string("Message.Error.WrongArgument"))
                        }
                    } catch (e: IllegalArgumentException) {
                        throw CommandException(e.toString())
                    }
                }
                "spectate" -> {
                    commandRequirePlayer(player)
                    commandRequireArgumentsMin(args, 2)
                    val toSpectate = GregChessInfo.server.getPlayer(args[1])
                    commandRequireNotNull(toSpectate, string("Message.Error.PlayerNotFound"))
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
                "reload" -> {
                    commandRequirePermission(player, "greg-chess.debug")
                    commandRequireArgumentsMin(args, 1)
                    plugin.reloadConfig()
                    reloadArenas()
                }
                else -> throw CommandException(string("Message.Error.WrongArgument"))
            }
        }
        plugin.addCommandTab("chess") { s, args ->
            fun <T> ifPermission(vararg list: T) =
                if (s.hasPermission("greg-chess.debug")) list.map { it.toString() } else emptyList()

            when (args.size) {
                1 -> listOf("duel", "stockfish", "resign", "leave", "draw", "save", "spectate") +
                        ifPermission("capture", "spawn", "move", "skip", "load", "time", "uci")
                2 -> when (args[0]) {
                    "duel" -> null
                    "spawn" -> ifPermission(*ChessSide.values())
                    "time" -> ifPermission(*ChessSide.values())
                    "uci" -> ifPermission("set", "send")
                    "spectate" -> null
                    else -> listOf()
                }
                3 -> when (args[0]) {
                    "spawn" -> ifPermission(*ChessPiece.Type.values())
                    "time" -> ifPermission("add", "set")
                    else -> listOf()
                }
                else -> listOf()
            }?.filter { it.startsWith(args.last()) }
        }
    }

    fun stop() {
        players.forEachGame { it.stop(ChessGame.EndReason.PluginRestart(), it.realPlayers) }
        arenas.forEach { it.delete() }
    }

    fun reloadArenas(){

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
