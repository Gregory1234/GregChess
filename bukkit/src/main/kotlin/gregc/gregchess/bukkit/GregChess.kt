package gregc.gregchess.bukkit

import gregc.gregchess.*
import gregc.gregchess.bukkit.chess.*
import gregc.gregchess.bukkit.chess.component.*
import gregc.gregchess.bukkit.command.*
import gregc.gregchess.bukkit.coroutines.BukkitContext
import gregc.gregchess.bukkit.coroutines.BukkitScope
import gregc.gregchess.chess.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

object GregChess : Listener {
    class Plugin : JavaPlugin(), BukkitChessPlugin {
        companion object {
            lateinit var INSTANCE: Plugin
                private set
        }

        init {
            INSTANCE = this
        }

        override fun onEnable() {
            GregChess.onEnable()
        }

        override fun onDisable() {
            GregChess.onDisable()
        }

        override fun onInitialize() {
            GregChessModule.logger = JavaGregLogger(GregChess.logger)
            GregChessModule.extensions += BukkitGregChessModule
            GregChessModule.fullLoad()
        }
    }

    val coroutineScope = BukkitScope(plugin, BukkitContext.SYNC)

    val logger get() = plugin.logger

    val plugin get() = Plugin.INSTANCE

    private val CLOCK_NOT_FOUND = err("ClockNotFound")
    private val ENGINE_NOT_FOUND = err("EngineNotFound")
    private val STOCKFISH_NOT_FOUND = err("StockfishNotFound")
    private val PIECE_NOT_FOUND = err("PieceNotFound")
    private val GAME_NOT_FOUND = err("GameNotFound")
    private val NOTHING_TO_TAKEBACK = err("NothingToTakeback")
    private val NO_ARENAS = err("NoArenas")

    private val BOARD_OP_DONE = message("BoardOpDone")
    private val SKIPPED_TURN = message("SkippedTurn")
    private val TIME_OP_DONE = message("TimeOpDone")
    private val ENGINE_COMMAND_SENT = message("EngineCommandSent")
    private val LOADED_FEN = message("LoadedFEN")
    private val CONFIG_RELOADED = message("ConfigReloaded")

    private val drawRequest = RequestManager.register("Draw", "/chess draw", "/chess draw")

    private val takebackRequest = RequestManager.register("Takeback", "/chess undo", "/chess undo")

    private val duelRequest = RequestManager.register("Duel", "/chess duel accept", "/chess duel cancel")

    @OptIn(ExperimentalSerializationApi::class)
    fun onEnable() {
        registerEvents()
        plugin.saveDefaultConfig()
        ExtensionType.extensionTypes += BukkitChessExtension.BUKKIT
        for (p in plugin.server.pluginManager.plugins)
            if (p is BukkitChessPlugin)
                p.onInitialize()
        ChessGameManager.start()
        Arena.reloadArenas()
        RequestManager.start()

        val json = Json { serializersModule = defaultModule() }
        // TODO: fix leave and resign throwing weird exceptions
        plugin.addCommand("chess") {
            subcommand("duel") {
                requirePlayer()
                requireNoGame()
                literal("accept") {
                    argument(UUIDArgument("request")) { req ->
                        execute<Player> {
                            cWrongArgument {
                                duelRequest.accept(sender, req())
                            }
                        }
                    }
                }
                literal("cancel") {
                    argument(UUIDArgument("request")) { req ->
                        execute<Player> {
                            cWrongArgument {
                                duelRequest.cancel(sender, req())
                            }
                        }
                    }
                }
                argument(PlayerArgument("opponent")) { opponentArg ->
                    filter {
                        it.filter { p -> Bukkit.getPlayer(p)?.isInGame == false }.toSet()
                    }

                    execute<Player> {
                        val opponent = opponentArg()
                        cRequire(!opponent.isInGame, OPPONENT_IN_GAME)
                        coroutineScope.launch {
                            val settings = try {
                                sender.openSettingsMenu()
                            } catch (e: NoFreeArenasException) {
                                throw CommandException(NO_ARENAS)
                            }

                            if (settings != null) {
                                val res = duelRequest.call(RequestData(sender, opponent, settings.name))
                                if (res == RequestResponse.ACCEPT) {
                                    ChessGame(settings, byColor(sender.cpi, opponent.cpi)).start()
                                }
                            }
                        }.passExceptions()
                    }
                }
            }
            subcommand("stockfish") {
                requirePlayer()
                requireNoGame()
                execute<Player> {
                    cRequire(Stockfish.Config.hasStockfish, STOCKFISH_NOT_FOUND)
                    coroutineScope.launch {
                        val settings = sender.openSettingsMenu()
                        if (settings != null)
                            ChessGame(settings, byColor(sender.cpi, Stockfish())).start()
                    }.passExceptions()
                }
            }
            subcommand("resign") {
                val pl = requireGame()
                execute<Player> {
                    pl().game.stop(pl().color.lostBy(EndReason.RESIGNATION))
                }
            }
            subcommand("leave") {
                requirePlayer()
                filter {
                    if (sender is Player && (sender.isInGame || sender.isSpectating)) it else null
                }
                execute<Player> {
                    cRequire(sender.isInGame || sender.isSpectating, YOU_NOT_IN_GAME)
                    ChessGameManager.leave(sender)
                }
            }
            subcommand("draw") {
                val pl = requireGame()
                val op = requireHumanOpponent()
                execute<Player> {
                    coroutineScope.launch {
                        drawRequest.invalidSender(sender) { !pl().hasTurn }
                        val res = drawRequest.call(RequestData(sender, op().player, ""), true)
                        if (res == RequestResponse.ACCEPT) {
                            pl().game.stop(drawBy(EndReason.DRAW_AGREEMENT))
                        }
                    }.passExceptions()
                }
            }
            subcommand("capture") {
                val pl = requireGame()
                execute<Player> {
                    val g = pl().game
                    val pos = g.renderer.getPos(sender.location.toLoc())
                    g.board[pos]?.piece?.capture(pl().color, g.board)
                    g.board.updateMoves()
                    sender.sendMessage(BOARD_OP_DONE)
                }
                argument(PosArgument("pos")) { pos ->
                    execute<Player> {
                        val g = pl().game
                        g.board[pos()]?.piece?.capture(pl().color, g.board)
                        g.board.updateMoves()
                        sender.sendMessage(BOARD_OP_DONE)
                    }
                }
            }
            subcommand("spawn") {
                val pl = requireGame()
                argument(RegistryArgument("piece", PieceRegistryView)) { piece ->
                    execute<Player> {
                        val g = pl().game
                        val square = g.board[g.renderer.getPos(sender.location.toLoc())]!!
                        square.piece?.capture(pl().color, square.board)
                        g.board += BoardPiece(square.pos, piece(), false)
                        square.piece?.sendCreated(g.board)
                        g.board.updateMoves()
                        sender.sendMessage(BOARD_OP_DONE)
                    }
                    argument(PosArgument("pos")) { pos ->
                        execute<Player> {
                            val g = pl().game
                            val square = g.board[pos()]!!
                            square.piece?.capture(pl().color, square.board)
                            g.board += BoardPiece(square.pos, piece(), false)
                            square.piece?.sendCreated(g.board)
                            g.board.updateMoves()
                            sender.sendMessage(BOARD_OP_DONE)
                        }
                    }
                }
            }
            subcommand("move") {
                val pl = requireGame()
                argument(PosArgument("from")) { from ->
                    argument(PosArgument("to")) { to ->
                        execute<Player> {
                            val g = pl().game
                            g.board[to()]?.piece?.capture(pl().color, g.board)
                            g.board[from()]?.piece?.move(to(), g.board)
                            g.board.updateMoves()
                            sender.sendMessage(BOARD_OP_DONE)
                        }
                    }
                }
            }
            subcommand("skip") {
                val pl = requireGame()
                execute<Player> {
                    pl().game.nextTurn()
                    sender.sendMessage(SKIPPED_TURN)
                }
            }
            subcommand("load") {
                val pl = requireGame()
                argument(FENArgument("fen")) { fen ->
                    execute<Player> {
                        pl().game.board.setFromFEN(fen())
                    }
                }
            }
            subcommand("save") {
                val pl = requireGame()
                execute<Player> {
                    sender.sendFEN(pl().game.board.getFEN())
                }
            }
            subcommand("time") {
                val pl = requireGame()
                filter {
                    if ((sender as? Player)?.currentGame?.clock != null) it else null
                }
                partialExecute<Player> {
                    pl().game.clock.cNotNull(CLOCK_NOT_FOUND)
                }
                argument(enumArgument<Color>("side")) { side ->
                    literal("set") {
                        argument(DurationArgument("time")) { time ->
                            execute<Player> {
                                pl().game.clock!!.setTimer(side(), time())
                                sender.sendMessage(TIME_OP_DONE)
                            }
                        }
                    }
                    literal("add") {
                        argument(DurationArgument("time")) { time ->
                            execute<Player> {
                                pl().game.clock!!.addTimer(side(), time())
                                sender.sendMessage(TIME_OP_DONE)
                            }
                        }
                    }
                }
            }
            subcommand("uci") {
                val pl = requireGame()
                filter {
                    if ((sender as? Player)?.currentGame?.players?.toList()?.filterIsInstance<EnginePlayer>()?.firstOrNull() == null) null else it
                }
                partialExecute<Player> {
                    pl().game.players.toList().filterIsInstance<EnginePlayer>().firstOrNull().cNotNull(ENGINE_NOT_FOUND)
                }
                literal("set") {
                    argument(StringArgument("option")) { option ->
                        argument(GreedyStringArgument("value")) { value ->
                            execute<Player> {
                                pl().game.players.toList().filterIsInstance<EnginePlayer>().first().engine.setOption(option(), value())
                                sender.sendMessage(ENGINE_COMMAND_SENT)
                            }
                        }
                    }
                }
                literal("send") {
                    argument(GreedyStringArgument("command")) { command ->
                        execute<Player> {
                            pl().game.players.toList().filterIsInstance<EnginePlayer>().first().engine.sendCommand(command())
                            sender.sendMessage(ENGINE_COMMAND_SENT)
                        }
                    }
                }
            }
            subcommand("spectate") {
                requirePlayer()
                requireNoGame()
                argument(PlayerArgument("spectated")) { spectated ->
                    filter {
                        it.filter { p -> Bukkit.getPlayer(p)?.isInGame == true }.toSet()
                    }
                    execute<Player> {
                        sender.spectatedGame = spectated().currentGame.cNotNull(PLAYER_NOT_IN_GAME)
                    }
                }
            }
            subcommand("reload") {
                execute {
                    plugin.reloadConfig()
                    Arena.reloadArenas()
                    sender.sendMessage(CONFIG_RELOADED.get())
                }
            }
            subcommand("dev") {
                execute {
                    Bukkit.dispatchCommand(sender, "devhelp GregChess ${plugin.description.version}")
                }
            }
            subcommand("undo") {
                val pl = requireGame()
                filter {
                    if ((sender as? Player)?.currentGame?.board?.lastMove == null || sender.chess?.opponent !is BukkitPlayer) null else it
                }
                execute<Player> {
                    val opponent: BukkitPlayer = pl().opponent.cCast(OPPONENT_NOT_HUMAN)
                    coroutineScope.launch {
                        drawRequest.invalidSender(sender) {
                            (pl().game.currentOpponent as? BukkitPlayer)?.player != sender
                        }
                        val res = takebackRequest.call(RequestData(sender, opponent.player, ""), true)
                        if (res == RequestResponse.ACCEPT) {
                            pl().game.board.undoLastMove()
                        }
                    }.passExceptions()
                }
            }
            subcommand("serial") {
                val pl = requireGame()
                execute<Player> {
                    logger.info(json.encodeToString(pl().game))
                }
            }
            subcommand("serialsave") {
                val pl = requireGame()
                argument(StringArgument("name")) { name ->
                    execute<Player> {
                        val f = File(plugin.dataFolder, "snapshots/${name()}.json")
                        f.parentFile.mkdirs()
                        f.createNewFile()
                        f.writeText(json.encodeToString(pl().game))
                    }
                }
            }
            subcommand("serialload") {
                requirePlayer()
                requireNoGame()
                argument(StringArgument("name")) { name ->
                    execute<Player> {
                        val f = File(plugin.dataFolder, "snapshots/${name()}.json")
                        json.decodeFromString<ChessGame>(f.readText()).sync()
                    }
                }
            }
            literal("info") {
                literal("game") {
                    filter {
                        if (sender.hasPermission("greg-chess.chess.info.ingame") && (sender as? Player)?.currentGame != null) it else null
                    }
                    execute {
                        (sender as? Player).cNotNull(NOT_PLAYER)
                        sender.cPerms("greg-chess.chess.info.ingame")
                    }
                    execute<Player> {
                        sender.spigot().sendMessage((sender as? Player)?.currentGame.cNotNull(YOU_NOT_IN_GAME).getInfo())
                    }
                    argument(UUIDArgument("game")) { game ->
                        requirePermission("greg-chess.chess.info.remote")
                        execute {
                            sender.spigot().sendMessage(ChessGameManager[game()].cNotNull(GAME_NOT_FOUND).getInfo())
                        }
                    }
                }
                literal("piece") {
                    requirePermission("greg-chess.chess.info.ingame")
                    val pl = requireGame()
                    execute<Player> {
                        sender.spigot().sendMessage(
                            pl().game.board[pl().game.renderer.getPos(sender.location.toLoc())]?.piece
                                .cNotNull(PIECE_NOT_FOUND).getInfo(pl().game)
                        )
                    }
                    argument(PosArgument("pos")) { pos ->
                        execute<Player> {
                            sender.spigot().sendMessage(
                                pl().game.board[pos()]?.piece.cNotNull(PIECE_NOT_FOUND).getInfo(pl().game))
                        }
                    }
                }
            }
            subcommand("admin") {
                requirePlayer()
                execute<Player> {
                    sender.isAdmin = !sender.isAdmin
                }
            }
        }
    }

    fun onDisable() {
        ChessGameManager.stop()
        coroutineScope.cancel()
    }

    @EventHandler
    fun onTurnEnd(e: TurnEndEvent) {
        if (e.player is BukkitPlayer) {
            drawRequest.quietRemove(e.player.player)
            takebackRequest.quietRemove(e.player.player)
        }
    }

    @EventHandler
    fun onGameEnd(e: GameEndEvent) {
        e.game.players.forEachReal {
            drawRequest.quietRemove(it)
            takebackRequest.quietRemove(it)
        }
    }

    @EventHandler
    fun onInventoryClick(e: InventoryClickEvent) {
        val holder = e.inventory.holder
        if (holder is Menu<*>) {
            e.isCancelled = true
            cTry(e.whoClicked, { e.whoClicked.closeInventory() }) {
                if (!holder.finished)
                    if (holder.click(e.slot.toInvPos()))
                        e.whoClicked.closeInventory()
            }
        }
    }

    @EventHandler
    fun onInventoryClose(e: InventoryCloseEvent) {
        val holder = e.inventory.holder
        if (holder is Menu<*>) {
            cTry(e.player) {
                if (!holder.finished)
                    holder.cancel()
            }
        }
    }
}
