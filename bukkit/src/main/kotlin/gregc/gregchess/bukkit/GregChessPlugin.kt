package gregc.gregchess.bukkit

import gregc.gregchess.bukkit.chess.*
import gregc.gregchess.bukkit.chess.component.*
import gregc.gregchess.bukkit.chess.player.*
import gregc.gregchess.bukkit.command.*
import gregc.gregchess.bukkitutils.*
import gregc.gregchess.bukkitutils.command.*
import gregc.gregchess.bukkitutils.coroutines.BukkitContext
import gregc.gregchess.bukkitutils.coroutines.BukkitScope
import gregc.gregchess.bukkitutils.requests.*
import gregc.gregchess.clock.clock
import gregc.gregchess.game.ChessGame
import gregc.gregchess.move.scheme.*
import gregc.gregchess.piece.BoardPiece
import gregc.gregchess.piece.PieceRegistryView
import gregc.gregchess.player.EngineChessSide
import gregc.gregchess.player.toPlayer
import gregc.gregchess.registry.Registry
import gregc.gregchess.results.*
import gregc.gregchess.stats.ChessStat
import gregc.gregchess.util.Color
import gregc.gregchess.util.byColor
import kotlinx.coroutines.*
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bukkit.command.CommandSender
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

        override fun onInitialize() = GregChess.fullLoad()
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
    private val NO_GAME_TO_REJOIN = err("NoGameToRejoin")

    private val BOARD_OP_DONE = message("BoardOpDone")
    private val SKIPPED_TURN = message("SkippedTurn")
    private val TIME_OP_DONE = message("TimeOpDone")
    private val ENGINE_COMMAND_SENT = message("EngineCommandSent")
    private val LOADED_FEN = message("LoadedFEN")
    private val CONFIG_RELOADED = message("ConfigReloaded")
    private val STATS_OP_DONE = message("StatsOpDone")
    private val ADMIN_TOGGLED = message("AdminToggled")

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
        ArenaManager.fromConfig().reloadArenas()
        requestManager.start()

        val json = Json { serializersModule = defaultModule() }

        CommandEnvironment(plugin, coroutineScope, WRONG_ARGUMENTS_NUMBER, WRONG_ARGUMENT, INTERNAL_ERROR, NOT_PLAYER).addCommand("chess") {
            playerSubcommand("duel") {
                requireNoGame()
                literal("accept") {
                    argument(uuidArgument("request")) { req ->
                        execute {
                            cWrongArgument {
                                duelRequest.accept(sender, req())
                            }
                        }
                    }
                }
                literal("cancel") {
                    argument(uuidArgument("request")) { req ->
                        execute {
                            cWrongArgument {
                                duelRequest.cancel(sender, req())
                            }
                        }
                    }
                }
                argument(playerArgument("opponent")) { opponentArg ->
                    validate(OPPONENT_IN_GAME) { !opponentArg().isInGame }

                    executeSuspend {
                        val opponent = opponentArg()
                        val settings = try {
                            sender.openSettingsMenu()
                        } catch (e: NoFreeArenasException) {
                            throw CommandException(NO_ARENAS)
                        }

                        if (settings != null) {
                            val res = duelRequest.call(RequestData(sender.uniqueId, opponent.uniqueId, settings.name))
                            if (res == RequestResponse.ACCEPT) {
                                ChessGame(BukkitChessEnvironment, settings.variant, settings.components, byColor(sender.gregchess, opponent.gregchess)).start()
                            }
                        }
                    }
                }
            }
            playerSubcommand("stockfish") {
                requireNoGame()
                executeSuspend {
                    cRequire(Stockfish.Config.hasStockfish, STOCKFISH_NOT_FOUND)
                    val settings = sender.openSettingsMenu()
                    if (settings != null)
                        ChessGame(BukkitChessEnvironment, settings.variant, settings.components, byColor(sender.gregchess, Stockfish().toPlayer())).start()
                }
            }
            playerSubcommand("resign") {
                val pl = requireGame()
                execute {
                    pl().game.stop(pl().color.lostBy(EndReason.RESIGNATION))
                }
            }
            playerSubcommand("leave") {
                validate(YOU_NOT_IN_GAME) { sender.isInGame || sender.isSpectating }
                execute {
                    sender.leaveGame()
                }
            }
            playerSubcommand("draw") {
                val pl = requireGame()
                val op = requireHumanOpponent()
                executeSuspend {
                    drawRequest.invalidSender(sender) { !pl().hasTurn }
                    val res = drawRequest.call(RequestData(sender.uniqueId, op().uuid, ""), true)
                    if (res == RequestResponse.ACCEPT) {
                        pl().game.stop(drawBy(EndReason.DRAW_AGREEMENT))
                    }
                }
            }
            playerSubcommand("capture") {
                val pl = requireGame()
                execute {
                    val g = pl().game
                    val piece = g.board[g.renderer.getPos(sender.location)]
                    if (piece != null) {
                        g.finishMove(phantomCapture(piece, pl().color))
                        sender.sendMessage(BOARD_OP_DONE)
                    } else {
                        sender.sendMessage(PIECE_NOT_FOUND)
                    }
                }
                argument(posArgument("pos")) { pos ->
                    execute {
                        val g = pl().game
                        val piece = g.board[pos()]
                        if (piece != null) {
                            g.finishMove(phantomCapture(piece, pl().color))
                            sender.sendMessage(BOARD_OP_DONE)
                        } else {
                            sender.sendMessage(PIECE_NOT_FOUND)
                        }
                    }
                }
            }
            playerSubcommand("spawn") {
                val pl = requireGame()
                argument(registryArgument("piece", PieceRegistryView)) { piece ->
                    execute {
                        val g = pl().game
                        g.finishMove(phantomSpawn(BoardPiece(g.renderer.getPos(sender.location), piece(), false)))
                        sender.sendMessage(BOARD_OP_DONE)
                    }
                    argument(posArgument("pos")) { pos ->
                        execute {
                            val g = pl().game
                            g.finishMove(phantomSpawn(BoardPiece(pos(), piece(), false)))
                            sender.sendMessage(BOARD_OP_DONE)
                        }
                    }
                }
            }
            playerSubcommand("move") {
                val pl = requireGame()
                argument(posArgument("from")) { from ->
                    argument(posArgument("to")) { to ->
                        execute {
                            val g = pl().game
                            val piece = g.board[from()]
                            if (piece != null) {
                                g.finishMove(phantomMove(piece, to()))
                                sender.sendMessage(BOARD_OP_DONE)
                            } else {
                                sender.sendMessage(PIECE_NOT_FOUND)
                            }
                        }
                    }
                }
            }
            playerSubcommand("skip") {
                val pl = requireGame()
                execute {
                    pl().game.nextTurn()
                    sender.sendMessage(SKIPPED_TURN)
                }
            }
            playerSubcommand("load") {
                val pl = requireGame()
                argument(FENArgument("fen")) { fen ->
                    execute {
                        pl().game.board.setFromFEN(fen())
                        sender.sendMessage(LOADED_FEN)
                    }
                }
            }
            playerSubcommand("save") {
                val pl = requireGame()
                execute {
                    sender.sendFEN(pl().game.board.getFEN())
                }
            }
            playerSubcommand("time") {
                val pl = requireGame()
                validate(CLOCK_NOT_FOUND) { sender.currentGame?.clock != null }
                argument(enumArgument<Color>("side")) { side ->
                    literal("set") {
                        argument(durationArgument("time")) { time ->
                            execute {
                                pl().game.clock!!.setTimer(side(), time())
                                sender.sendMessage(TIME_OP_DONE)
                            }
                        }
                    }
                    literal("add") {
                        argument(durationArgument("time")) { time ->
                            execute {
                                pl().game.clock!!.addTimer(side(), time())
                                sender.sendMessage(TIME_OP_DONE)
                            }
                        }
                    }
                }
            }
            playerSubcommand("uci") {
                val pl = requireGame()
                validate(ENGINE_NOT_FOUND) {
                    sender.currentGame?.sides?.toList()?.filterIsInstance<EngineChessSide<*>>()?.firstOrNull() != null
                }
                literal("set") {
                    argument(stringArgument("option")) { option ->
                        argument(GreedyStringArgument("value")) { value ->
                            executeSuspend {
                                pl().game.sides.toList().filterIsInstance<EngineChessSide<*>>()
                                    .first().engine.setOption(option(), value())
                                sender.sendMessage(ENGINE_COMMAND_SENT)
                            }
                        }
                    }
                }
                literal("send") {
                    argument(GreedyStringArgument("command")) { command ->
                        executeSuspend {
                            pl().game.sides.toList().filterIsInstance<EngineChessSide<*>>()
                                .first().engine.sendCommand(command())
                            sender.sendMessage(ENGINE_COMMAND_SENT)
                        }
                    }
                }
            }
            playerSubcommand("spectate") {
                requireNoGame()
                argument(playerArgument("spectated")) { spectated ->
                    validate(PLAYER_NOT_IN_GAME) { spectated().isInGame }
                    execute {
                        sender.spectatedGame = spectated().currentGame!!
                    }
                }
            }
            subcommand("reload") {
                execute {
                    val oldManager = ArenaManager.fromConfig()
                    plugin.reloadConfig()
                    if (ArenaManager.fromConfig() != oldManager) oldManager.unloadArenas()
                    ArenaManager.fromConfig().reloadArenas()
                    sender.sendMessage(CONFIG_RELOADED)
                }
            }
            playerSubcommand("undo") {
                val pl = requireGame()
                validate(NOTHING_TO_TAKEBACK) { sender.currentGame?.board?.lastMove != null }
                val op = requireHumanOpponent()
                executeSuspend {
                    takebackRequest.invalidSender(sender) {
                        (pl().game.currentOpponent as? BukkitChessSide)?.uuid != sender.uniqueId
                    }
                    val res = takebackRequest.call(RequestData(sender.uniqueId, op().uuid, ""), true)
                    if (res == RequestResponse.ACCEPT) {
                        pl().game.board.undoLastMove()
                    }
                }
            }
            playerSubcommand("serial") {
                literal("save") {
                    val pl = requireGame()
                    argument(stringArgument("name")) { name ->
                        execute {
                            val f = plugin.dataFolder.resolve("snapshots/${name()}.json")
                            f.parentFile.mkdirs()
                            @Suppress("BlockingMethodInNonBlockingContext")
                            f.createNewFile()
                            f.writeText(json.encodeToString(pl().game))
                        }
                    }
                }
                literal("load") {
                    requireNoGame()
                    argument(stringArgument("name")) { name ->
                        execute {
                            val f = plugin.dataFolder.resolve("snapshots/${name()}.json")
                            json.decodeFromString<ChessGame>(f.readText()).sync()
                        }
                    }
                }
                execute {
                    GregChess.logger.info(json.encodeToString(sender.currentGame.cNotNull(YOU_NOT_IN_GAME)))
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
                    execute {
                        cRequire(sender.hasPermission("gregchess.chess.info.ingame"), NO_PERMISSION)
                        sender.spigot().sendMessage((sender as? Player)?.currentGame.cNotNull(YOU_NOT_IN_GAME).getInfo())
                    }
                    argument(uuidArgument("game")) { game ->
                        requirePermission("gregchess.chess.info.remote")
                        execute {
                            sender.spigot().sendMessage(ChessGameManager[game()].cNotNull(GAME_NOT_FOUND).getInfo())
                        }
                    }
                }
                literal(Player::class, "piece") {
                    requirePermission("gregchess.chess.info.ingame")
                    val pl = requireGame()
                    execute {
                        sender.spigot().sendMessage(
                            pl().game.board[pl().game.renderer.getPos(sender.location)]
                                .cNotNull(PIECE_NOT_FOUND).getInfo(pl().game)
                        )
                    }
                    argument(posArgument("pos")) { pos ->
                        execute {
                            sender.spigot().sendMessage(
                                pl().game.board[pos()].cNotNull(PIECE_NOT_FOUND).getInfo(pl().game))
                        }
                    }
                }
            }
            playerSubcommand("admin") {
                execute {
                    sender.isAdmin = !sender.isAdmin
                    sender.sendMessage(ADMIN_TOGGLED)
                }
            }
            subcommand("stats") {
                validate(NO_PERMISSION) {
                    sender.hasPermission("gregchess.chess.stats.self")
                            || sender.hasPermission("gregchess.chess.stats.read")
                            || sender.hasPermission("gregchess.chess.stats.set")
                }
                executeSuspend {
                    cRequire(sender.hasPermission("gregchess.chess.stats.self"), NO_PERMISSION)
                    val pl = sender.cCast<CommandSender, Player>(NOT_PLAYER)
                    pl.openStatsMenu(pl.name, BukkitPlayerStats.of(pl.uniqueId))
                }
                literal("set") {
                    requirePermission("gregchess.chess.stats.set")
                    argument(offlinePlayerArgument("player")) { player ->
                        argument(enumArgument<Color>("color")) { color ->
                            argument(stringArgument("setting")) { setting ->
                                argument(registryArgument("stat", Registry.STAT) { it.serializer == Int.serializer() }) { stat ->
                                    argument(intArgument("value")) { v ->
                                        execute {
                                            val stats = BukkitPlayerStats.of(player().uniqueId)
                                            @Suppress("UNCHECKED_CAST")
                                            stats[color(), setting()].add(stat() as ChessStat<Int>, v())
                                            sender.sendMessage(STATS_OP_DONE)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                literal("clear") {
                    requirePermission("gregchess.chess.stats.set")
                    argument(offlinePlayerArgument("player")) { player ->
                        argument(stringArgument("setting")) { setting ->
                            execute {
                                BukkitPlayerStats.of(player().uniqueId).clear(setting())
                                sender.sendMessage(STATS_OP_DONE)
                            }
                        }
                    }
                }
                argument(Player::class, offlinePlayerArgument("player")) { player ->
                    requirePermission("gregchess.chess.stats.read")
                    executeSuspend {
                        sender.openStatsMenu(player().name!!, BukkitPlayerStats.of(player().uniqueId))
                    }
                }
            }
            playerSubcommand("rejoin") {
                validate(WRONG_ARGUMENT) { config.getBoolean("Rejoin.AllowRejoining") }
                validate(YOU_IN_GAME) { !sender.isInGame && !sender.isSpectating }
                validate(NO_GAME_TO_REJOIN) { sender.lastLeftGame != null }
                execute {
                    sender.rejoinGame()
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
            drawRequest.quietRemove(e.player.uuid)
            takebackRequest.quietRemove(e.player.uuid)
        }
    }

    @EventHandler
    fun onGameEnd(e: GameEndEvent) {
        e.game.sides.forEachRealOffline {
            drawRequest.quietRemove(it.uniqueId)
            takebackRequest.quietRemove(it.uniqueId)
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
