package gregc.gregchess.bukkit

import gregc.gregchess.GregChess
import gregc.gregchess.bukkit.chess.*
import gregc.gregchess.bukkit.chess.component.*
import gregc.gregchess.bukkit.chess.player.*
import gregc.gregchess.bukkit.command.*
import gregc.gregchess.bukkitutils.*
import gregc.gregchess.bukkitutils.command.*
import gregc.gregchess.bukkitutils.coroutines.BukkitContext
import gregc.gregchess.bukkitutils.coroutines.BukkitScope
import gregc.gregchess.bukkitutils.requests.*
import gregc.gregchess.chess.*
import gregc.gregchess.chess.piece.*
import gregc.gregchess.chess.player.EngineChessSide
import kotlinx.coroutines.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.plugin.java.JavaPlugin

object GregChessPlugin : Listener {
    class Plugin : JavaPlugin(), BukkitChessPlugin {
        companion object {
            lateinit var INSTANCE: Plugin
                private set
        }

        init {
            INSTANCE = this
        }

        override fun onEnable() = GregChessPlugin.onEnable()

        override fun onDisable() = GregChessPlugin.onDisable()

        override fun onInitialize() = GregChess.fullLoad(listOf(GregChessBukkit))
    }

    val coroutineScope = BukkitScope(plugin, BukkitContext.SYNC) + CoroutineExceptionHandler { _, e ->
        e.printStackTrace()
    }

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

    private val requestManager = RequestManager(plugin, coroutineScope)

    private val drawRequest = requestManager.register("Draw", "/chess draw", "/chess draw")

    private val takebackRequest = requestManager.register("Takeback", "/chess undo", "/chess undo")

    private val duelRequest = requestManager.register("Duel", "/chess duel accept", "/chess duel cancel")

    fun onEnable() {
        registerEvents()
        plugin.saveDefaultConfig()
        for (p in plugin.server.pluginManager.plugins)
            if (p is BukkitChessPlugin)
                p.onInitialize()
        ChessGameManager.start()
        Arena.reloadArenas()
        requestManager.start()

        val json = Json { serializersModule = defaultModule() }

        CommandEnvironment(plugin, coroutineScope, WRONG_ARGUMENTS_NUMBER, WRONG_ARGUMENT, INTERNAL_ERROR).addCommand("chess") {
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
                argument(playerArgument("opponent")) { opponentArg ->
                    validate(OPPONENT_IN_GAME) { !opponentArg().isInGame }

                    executeSuspend<Player> {
                        val opponent = opponentArg()
                        val settings = try {
                            sender.openSettingsMenu()
                        } catch (e: NoFreeArenasException) {
                            throw CommandException(NO_ARENAS)
                        }

                        if (settings != null) {
                            val res = duelRequest.call(RequestData(sender.uniqueId, opponent.uniqueId, settings.name))
                            if (res == RequestResponse.ACCEPT) {
                                ChessGame(BukkitChessEnvironment, settings, byColor(sender.gregchess, opponent.gregchess)).start()
                            }
                        }
                    }
                }
            }
            subcommand("stockfish") {
                requirePlayer()
                requireNoGame()
                executeSuspend<Player> {
                    cRequire(Stockfish.Config.hasStockfish, STOCKFISH_NOT_FOUND)
                    val settings = sender.openSettingsMenu()
                    if (settings != null)
                        ChessGame(BukkitChessEnvironment, settings, byColor(sender.gregchess, Stockfish())).start()
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
                validate(YOU_NOT_IN_GAME) { sender !is Player || (sender as Player).isInGame || (sender as Player).isSpectating }
                execute<Player> {
                    ChessGameManager.leave(sender)
                }
            }
            subcommand("draw") {
                val pl = requireGame()
                val op = requireHumanOpponent()
                executeSuspend<Player> {
                    drawRequest.invalidSender(sender) { !pl().hasTurn }
                    val res = drawRequest.call(RequestData(sender.uniqueId, op().player.uuid, ""), true)
                    if (res == RequestResponse.ACCEPT) {
                        pl().game.stop(drawBy(EndReason.DRAW_AGREEMENT))
                    }
                }
            }
            subcommand("capture") {
                val pl = requireGame()
                execute<Player> {
                    val g = pl().game
                    val pos = g.renderer.getPos(sender.location)
                    multiMove(g, g.board[pos]?.capture(pl().color))
                    g.board.updateMoves(g)
                    sender.sendMessage(BOARD_OP_DONE)
                }
                argument(PosArgument("pos")) { pos ->
                    execute<Player> {
                        val g = pl().game
                        multiMove(g, g.board[pos()]?.capture(pl().color))
                        g.board.updateMoves(g)
                        sender.sendMessage(BOARD_OP_DONE)
                    }
                }
            }
            subcommand("spawn") {
                val pl = requireGame()
                argument(RegistryArgument("piece", PieceRegistryView)) { piece ->
                    execute<Player> {
                        val g = pl().game
                        val p = g.renderer.getPos(sender.location)
                        multiMove(g, g.board[p]?.capture(pl().color), null to BoardPiece(p, piece(), false))
                        g.board.updateMoves(g)
                        sender.sendMessage(BOARD_OP_DONE)
                    }
                    argument(PosArgument("pos")) { pos ->
                        execute<Player> {
                            val g = pl().game
                            multiMove(g, g.board[pos()]?.capture(pl().color), null to BoardPiece(pos(), piece(), false))
                            g.board.updateMoves(g)
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
                            multiMove(g, g.board[to()]?.capture(pl().color), g.board[from()]?.move(to()))
                            g.board.updateMoves(g)
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
                        pl().game.board.setFromFEN(pl().game, fen())
                        sender.sendMessage(LOADED_FEN)
                    }
                }
            }
            subcommand("save") {
                val pl = requireGame()
                execute<Player> {
                    sender.sendFEN(pl().game.board.getFEN(pl().game))
                }
            }
            subcommand("time") {
                val pl = requireGame()
                validate(CLOCK_NOT_FOUND) { (sender as? Player)?.currentGame?.clock != null }
                argument(enumArgument<Color>("side")) { side ->
                    literal("set") {
                        argument(durationArgument("time")) { time ->
                            execute<Player> {
                                pl().game.clock!!.setTimer(side(), time())
                                sender.sendMessage(TIME_OP_DONE)
                            }
                        }
                    }
                    literal("add") {
                        argument(durationArgument("time")) { time ->
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
                validate(ENGINE_NOT_FOUND) {
                    (sender as? Player)?.currentGame?.sides?.toList()
                        ?.filterIsInstance<EngineChessSide<*>>()?.firstOrNull() != null
                }
                literal("set") {
                    argument(StringArgument("option")) { option ->
                        argument(GreedyStringArgument("value")) { value ->
                            executeSuspend<Player> {
                                pl().game.sides.toList().filterIsInstance<EngineChessSide<*>>()
                                    .first().engine.setOption(option(), value())
                                sender.sendMessage(ENGINE_COMMAND_SENT)
                            }
                        }
                    }
                }
                literal("send") {
                    argument(GreedyStringArgument("command")) { command ->
                        executeSuspend<Player> {
                            pl().game.sides.toList().filterIsInstance<EngineChessSide<*>>()
                                .first().engine.sendCommand(command())
                            sender.sendMessage(ENGINE_COMMAND_SENT)
                        }
                    }
                }
            }
            subcommand("spectate") {
                requirePlayer()
                requireNoGame()
                argument(playerArgument("spectated")) { spectated ->
                    validate(PLAYER_NOT_IN_GAME) { spectated().isInGame }
                    execute<Player> {
                        sender.spectatedGame = spectated().currentGame!!
                    }
                }
            }
            subcommand("reload") {
                execute {
                    plugin.reloadConfig()
                    Arena.reloadArenas()
                    sender.sendMessage(CONFIG_RELOADED)
                }
            }
            subcommand("dev") {
                execute {
                    Bukkit.dispatchCommand(sender, "devhelp GregChess")
                }
            }
            subcommand("undo") {
                val pl = requireGame()
                validate(NOTHING_TO_TAKEBACK) { (sender as? Player)?.currentGame?.board?.lastMove != null }
                validate(OPPONENT_NOT_HUMAN) { (sender as? Player)?.chess?.opponent is BukkitChessSide }
                executeSuspend<Player> {
                    val opponent = pl().opponent as BukkitChessSide
                    drawRequest.invalidSender(sender) {
                        (pl().game.currentOpponent as? BukkitChessSide)?.player?.uuid != sender.uniqueId
                    }
                    val res = takebackRequest.call(RequestData(sender.uniqueId, opponent.player.uuid, ""), true)
                    if (res == RequestResponse.ACCEPT) {
                        pl().game.board.undoLastMove(pl().game)
                    }
                }
            }
            subcommand("serial") {
                val pl = requireGame()
                execute<Player> {
                    GregChess.logger.info(json.encodeToString(pl().game))
                }
            }
            subcommand("serialsave") {
                val pl = requireGame()
                argument(StringArgument("name")) { name ->
                    execute<Player> {
                        val f = plugin.dataFolder.resolve("snapshots/${name()}.json")
                        f.parentFile.mkdirs()
                        @Suppress("BlockingMethodInNonBlockingContext")
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
                        val f = plugin.dataFolder.resolve("snapshots/${name()}.json")
                        json.decodeFromString<ChessGame>(f.readText()).sync()
                    }
                }
            }
            literal("info") {
                validate(NO_PERMISSION) {
                    sender.hasPermission("gregchess.chess.info.ingame")
                        || sender.hasPermission("gregchess.chess.info.remote")
                }
                literal("game") {
                    execute {
                        cRequire(sender is Player, NOT_PLAYER)
                    }
                    execute<Player> {
                        cRequire(sender.hasPermission("gregchess.chess.info.ingame"), NO_PERMISSION)
                        sender.spigot().sendMessage((sender as? Player)?.currentGame.cNotNull(YOU_NOT_IN_GAME).getInfo())
                    }
                    argument(UUIDArgument("game")) { game ->
                        requirePermission("gregchess.chess.info.remote")
                        execute {
                            sender.spigot().sendMessage(ChessGameManager[game()].cNotNull(GAME_NOT_FOUND).getInfo())
                        }
                    }
                }
                literal("piece") {
                    requirePermission("gregchess.chess.info.ingame")
                    val pl = requireGame()
                    execute<Player> {
                        sender.spigot().sendMessage(
                            pl().game.board[pl().game.renderer.getPos(sender.location)]
                                .cNotNull(PIECE_NOT_FOUND).getInfo(pl().game)
                        )
                    }
                    argument(PosArgument("pos")) { pos ->
                        execute<Player> {
                            sender.spigot().sendMessage(
                                pl().game.board[pos()].cNotNull(PIECE_NOT_FOUND).getInfo(pl().game))
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
            subcommand("stats") {
                requirePlayer()
                executeSuspend<Player> {
                    sender.openStatsMenu(sender.name, ChessStats.of(sender.uniqueId))
                }
                argument(offlinePlayerArgument("player")) { player ->
                    executeSuspend<Player> {
                        sender.openStatsMenu(player().name!!, ChessStats.of(player().uniqueId))
                    }
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
        if (e.player is BukkitChessSide) {
            drawRequest.quietRemove(e.player.player.uuid)
            takebackRequest.quietRemove(e.player.player.uuid)
        }
    }

    @EventHandler
    fun onGameEnd(e: GameEndEvent) {
        e.game.sides.forEachReal {
            drawRequest.quietRemove(it.uuid)
            takebackRequest.quietRemove(it.uuid)
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
