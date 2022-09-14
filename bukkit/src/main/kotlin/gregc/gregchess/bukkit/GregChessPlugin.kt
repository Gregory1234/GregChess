package gregc.gregchess.bukkit

import gregc.gregchess.Color
import gregc.gregchess.bukkit.command.*
import gregc.gregchess.bukkit.component.ComponentAlternative
import gregc.gregchess.bukkit.match.*
import gregc.gregchess.bukkit.piece.getInfo
import gregc.gregchess.bukkit.player.*
import gregc.gregchess.bukkit.renderer.ArenaManager
import gregc.gregchess.bukkit.renderer.renderer
import gregc.gregchess.bukkit.stats.BukkitPlayerStats
import gregc.gregchess.bukkit.stats.openStatsMenu
import gregc.gregchess.bukkitutils.*
import gregc.gregchess.bukkitutils.command.*
import gregc.gregchess.bukkitutils.coroutines.BukkitContext
import gregc.gregchess.bukkitutils.coroutines.BukkitScope
import gregc.gregchess.bukkitutils.requests.*
import gregc.gregchess.byColor
import gregc.gregchess.clock.clock
import gregc.gregchess.match.ChessMatch
import gregc.gregchess.move.*
import gregc.gregchess.piece.BoardPiece
import gregc.gregchess.piece.PieceRegistryView
import gregc.gregchess.player.*
import gregc.gregchess.registry.Registry
import gregc.gregchess.results.*
import gregc.gregchess.stats.ChessStat
import kotlinx.coroutines.*
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
    private val MATCH_NOT_FOUND = err("MatchNotFound")
    private val NOTHING_TO_TAKEBACK = err("NothingToTakeback")
    private val NO_MATCH_TO_REJOIN = err("NoMatchToRejoin")
    private val NO_ONE_TO_REMATCH = err("NoOneToRematch")
    private val NOT_YOUR_TURN = err("NotYourTurn")
    private val YOUR_TURN = err("YourTurn")

    private val BOARD_OP_DONE = message("BoardOpDone")
    private val SKIPPED_TURN = message("SkippedTurn")
    private val TIME_OP_DONE = message("TimeOpDone")
    private val ENGINE_COMMAND_SENT = message("EngineCommandSent")
    private val LOADED_FEN = message("LoadedFEN")
    private val CONFIG_RELOADED = message("ConfigReloaded")
    private val STATS_OP_DONE = message("StatsOpDone")

    private val requestManager = RequestManager(plugin, coroutineScope)

    private val duelRequest = requestManager.register("Duel", "/chess duel accept", "/chess duel cancel")

    private val rematchRequest = requestManager.register("Rematch", "/chess rematch accept", "/chess rematch cancel")

    fun onEnable() {
        registerEvents()
        plugin.saveDefaultConfig()
        for (p in plugin.server.pluginManager.plugins)
            if (p is BukkitChessPlugin)
                p.onInitialize()
        ChessMatchManager.start()
        ArenaManager.reloadArenas()
        requestManager.start()

        val json = Json { serializersModule = defaultModule() }

        CommandEnvironment(plugin, BukkitPlayerProvider, coroutineScope, WRONG_ARGUMENTS_NUMBER, WRONG_ARGUMENT, INTERNAL_ERROR, NOT_PLAYER).addCommand("chess") {
            playerSubcommand("duel") {
                requireNoMatch()
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
                    validate(OPPONENT_IN_MATCH) { !opponentArg().isInMatch }

                    executeSuspend {
                        val opponent = opponentArg()
                        val settings = sender.openSettingsMenu()

                        if (settings != null) {
                            val res = duelRequest.call(RequestData(sender, opponent, settings.name))
                            if (res == RequestResponse.ACCEPT) {
                                if (sender.isInMatch || sender.isSpectatingMatch) {
                                    sender.sendMessage(YOU_IN_MATCH)
                                } else if (opponent.isInMatch || opponent.isSpectatingMatch) {
                                    sender.sendMessage(OPPONENT_IN_MATCH)
                                } else {
                                    settings.components.require(ComponentAlternative.RENDERER).validate()
                                    settings.createMatch(byColor(sender, opponent)).start()
                                }
                            }
                        }
                    }
                }
            }
            playerSubcommand("stockfish") {
                requireNoMatch()
                executeSuspend {
                    cRequire(Stockfish.Config.hasStockfish, STOCKFISH_NOT_FOUND)
                    val settings = sender.openSettingsMenu()
                    if (settings != null) {
                        if (sender.isInMatch || sender.isSpectatingMatch) {
                            sender.sendMessage(YOU_IN_MATCH)
                        } else {
                            settings.components.require(ComponentAlternative.RENDERER).validate()
                            settings.createMatch(byColor(sender, Stockfish())).start()
                        }
                    }
                }
            }
            playerSubcommand("resign") {
                val pl = requireMatch()
                execute {
                    pl().match.stop(pl().color.lostBy(EndReason.RESIGNATION))
                }
            }
            playerSubcommand("leave") {
                validate(YOU_NOT_IN_MATCH) { sender.isInMatch || sender.isSpectatingMatch }
                execute {
                    if (sender.isInMatch) {
                        sender.leaveMatch()
                    } else {
                        sender.leaveSpectatedMatch()
                    }
                }
            }
            playerSubcommand("draw") {
                val pl = requireMatch()
                requireHumanOpponent()
                validate(NOT_YOUR_TURN) {
                    sender.currentSide?.hasTurn == true ||
                            (sender.currentSide?.opponent as? HumanChessSideFacade)?.isRequesting(HumanRequest.DRAW) == true
                }
                executeSuspend {
                    pl().toggleRequest(HumanRequest.DRAW)
                }
            }
            playerSubcommand("capture") {
                val pl = requireMatch()
                execute {
                    val g = pl().match
                    val piece = g.board[g.renderer.getPos(sender.entity!!.location)]
                    if (piece != null) {
                        g.finishMove(phantomCapture(piece, pl().color))
                        sender.sendMessage(BOARD_OP_DONE)
                    } else {
                        sender.sendMessage(PIECE_NOT_FOUND)
                    }
                }
                argument(posArgument("pos")) { pos ->
                    execute {
                        val g = pl().match
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
                val pl = requireMatch()
                argument(registryArgument("piece", PieceRegistryView)) { piece ->
                    execute {
                        val g = pl().match
                        g.finishMove(phantomSpawn(BoardPiece(g.renderer.getPos(sender.entity!!.location), piece(), false)))
                        sender.sendMessage(BOARD_OP_DONE)
                    }
                    argument(posArgument("pos")) { pos ->
                        execute {
                            val g = pl().match
                            g.finishMove(phantomSpawn(BoardPiece(pos(), piece(), false)))
                            sender.sendMessage(BOARD_OP_DONE)
                        }
                    }
                }
            }
            playerSubcommand("move") {
                val pl = requireMatch()
                argument(posArgument("from")) { from ->
                    argument(posArgument("to")) { to ->
                        execute {
                            val g = pl().match
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
                val pl = requireMatch()
                execute {
                    pl().match.nextTurn()
                    sender.sendMessage(SKIPPED_TURN)
                }
            }
            playerSubcommand("load") {
                val pl = requireMatch()
                argument(FENArgument("fen")) { fen ->
                    execute {
                        pl().match.board.setFromFEN(fen())
                        sender.sendMessage(LOADED_FEN)
                    }
                }
            }
            playerSubcommand("save") {
                val pl = requireMatch()
                execute {
                    sender.sendMessage(pl().match.board.getFEN().copyMessage())
                }
            }
            playerSubcommand("time") {
                val pl = requireMatch()
                validate(CLOCK_NOT_FOUND) { sender.currentMatch?.clock != null }
                argument(enumArgument<Color>("side")) { side ->
                    literal("set") {
                        argument(durationArgument("time")) { time ->
                            execute {
                                pl().match.clock!!.setTimer(side(), time())
                                sender.sendMessage(TIME_OP_DONE)
                            }
                        }
                    }
                    literal("add") {
                        argument(durationArgument("time")) { time ->
                            execute {
                                pl().match.clock!!.addTimer(side(), time())
                                sender.sendMessage(TIME_OP_DONE)
                            }
                        }
                    }
                }
            }
            playerSubcommand("uci") {
                val pl = requireMatch()
                validate(ENGINE_NOT_FOUND) {
                    sender.currentMatch?.sides?.toList()?.filterIsInstance<EngineChessSide<*>>()?.firstOrNull() != null
                }
                literal("set") {
                    argument(stringArgument("option")) { option ->
                        argument(GreedyStringArgument("value")) { value ->
                            executeSuspend {
                                pl().match.sides.toList().filterIsInstance<EngineChessSide<*>>()
                                    .first().engine.setOption(option(), value())
                                sender.sendMessage(ENGINE_COMMAND_SENT)
                            }
                        }
                    }
                }
                literal("send") {
                    argument(GreedyStringArgument("command")) { command ->
                        executeSuspend {
                            pl().match.sides.toList().filterIsInstance<EngineChessSide<*>>()
                                .first().engine.sendCommand(command())
                            sender.sendMessage(ENGINE_COMMAND_SENT)
                        }
                    }
                }
            }
            playerSubcommand("spectate") {
                requireNoMatch() // TODO: allow for switching spectated players
                argument(playerArgument("spectated")) { spectated ->
                    validate(PLAYER_NOT_IN_MATCH) { spectated().isInMatch }
                    execute {
                        sender.spectateMatch(spectated().currentMatch.cNotNull(PLAYER_NOT_IN_MATCH))
                    }
                }
            }
            subcommand("reload") {
                execute {
                    plugin.reloadConfig()
                    ArenaManager.reloadArenas()
                    sender.sendMessage(CONFIG_RELOADED)
                }
            }
            playerSubcommand("undo") {
                val pl = requireMatch()
                validate(NOTHING_TO_TAKEBACK) { sender.currentMatch?.board?.lastMove != null }
                requireHumanOpponent()
                validate(YOUR_TURN) {
                    (sender.currentMatch?.sides?.opponent as? BukkitChessSideFacade)?.player == sender
                            || (sender.currentSide?.opponent as? HumanChessSideFacade)?.isRequesting(HumanRequest.UNDO) == true
                }
                executeSuspend {
                    pl().toggleRequest(HumanRequest.UNDO)
                }
            }
            playerSubcommand("serial") {
                literal("save") {
                    val pl = requireMatch()
                    argument(stringArgument("name")) { name ->
                        execute {
                            val f = plugin.dataFolder.resolve("snapshots/${name()}.json")
                            f.parentFile.mkdirs()
                            f.createNewFile()
                            f.writeText(json.encodeToString(pl().match))
                        }
                    }
                }
                literal("load") {
                    requireNoMatch()
                    argument(stringArgument("name")) { name ->
                        execute {
                            val f = plugin.dataFolder.resolve("snapshots/${name()}.json")
                            json.decodeFromString<ChessMatch>(f.readText()).sync()
                        }
                    }
                }
                execute {
                    GregChess.logger.info(json.encodeToString(sender.currentMatch.cNotNull(YOU_NOT_IN_MATCH)))
                }
            }
            literal("info") {
                validate(NO_PERMISSION) {
                    sender.hasPermission("gregchess.chess.info.inmatch")
                        || sender.hasPermission("gregchess.chess.info.remote")
                }
                literal("match") {
                    execute {
                        cRequire(sender is BukkitPlayer, NOT_PLAYER)
                    }
                    execute {
                        cRequire(sender.hasPermission("gregchess.chess.info.inmatch"), NO_PERMISSION)
                        sender.sendMessage((sender as? BukkitPlayer)?.currentMatch.cNotNull(YOU_NOT_IN_MATCH).getInfo())
                    }
                    argument(uuidArgument("match")) { match ->
                        requirePermission("gregchess.chess.info.remote")
                        execute {
                            sender.sendMessage(ChessMatchManager[match()].cNotNull(MATCH_NOT_FOUND).getInfo())
                        }
                    }
                }
                literal(BukkitPlayer::class, "piece") {
                    requirePermission("gregchess.chess.info.inmatch")
                    val pl = requireMatch()
                    execute {
                        sender.sendMessage(
                            pl().match.board[pl().match.renderer.getPos(sender.entity!!.location)]
                                .cNotNull(PIECE_NOT_FOUND).getInfo(pl().match)
                        )
                    }
                    argument(posArgument("pos")) { pos ->
                        execute {
                            sender.sendMessage(
                                pl().match.board[pos()].cNotNull(PIECE_NOT_FOUND).getInfo(pl().match))
                        }
                    }
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
                    val pl = sender.cCast<_, BukkitPlayer>(NOT_PLAYER)
                    pl.openStatsMenu(pl.name, BukkitPlayerStats.of(pl.uuid))
                }
                literal("set") {
                    requirePermission("gregchess.chess.stats.set")
                    argument(offlinePlayerArgument("player")) { player ->
                        argument(enumArgument<Color>("color")) { color ->
                            argument(stringArgument("setting")) { setting ->
                                argument(registryArgument("stat", Registry.STAT) { it.serializer == Int.serializer() }) { stat ->
                                    argument(intArgument("value")) { v ->
                                        execute {
                                            val stats = BukkitPlayerStats.of(player().uuid)
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
                                BukkitPlayerStats.of(player().uuid).clear(setting())
                                sender.sendMessage(STATS_OP_DONE)
                            }
                        }
                    }
                }
                argument(BukkitPlayer::class, offlinePlayerArgument("player")) { player ->
                    requirePermission("gregchess.chess.stats.read")
                    executeSuspend {
                        sender.openStatsMenu(player().name, BukkitPlayerStats.of(player().uuid))
                    }
                }
            }
            playerSubcommand("rejoin") {
                validate(WRONG_ARGUMENT) { config.getBoolean("Rejoin.AllowRejoining") }
                requireNoMatch()
                validate(NO_MATCH_TO_REJOIN) { sender.activeMatches.isNotEmpty() }
                execute {
                    sender.joinMatch()
                }
            }
            playerSubcommand("rematch") {
                requireNoMatch()
                literal("accept") {
                    argument(uuidArgument("request")) { req ->
                        execute {
                            cWrongArgument {
                                rematchRequest.accept(sender, req())
                            }
                        }
                    }
                }
                literal("cancel") {
                    argument(uuidArgument("request")) { req ->
                        execute {
                            cWrongArgument {
                                rematchRequest.cancel(sender, req())
                            }
                        }
                    }
                }

                executeSuspend {
                    val rematchInfo = sender.rematchInfo.cNotNull(NO_ONE_TO_REMATCH)
                    val opponent = rematchInfo.target
                    val settings = SettingsManager.getSettings(rematchInfo.preset, rematchInfo.lastRound + 1)

                    if (settings != null) {
                        val res = rematchRequest.call(RequestData(sender, opponent, settings.name))
                        if (res == RequestResponse.ACCEPT) {
                            if (sender.isInMatch || sender.isSpectatingMatch) {
                                sender.sendMessage(YOU_IN_MATCH)
                            } else if (opponent.isInMatch || opponent.isSpectatingMatch) {
                                sender.sendMessage(OPPONENT_IN_MATCH)
                            } else {
                                settings.components.require(ComponentAlternative.RENDERER).validate()
                                settings.createMatch(if (rematchInfo.lastColor == Color.BLACK) byColor(sender, opponent) else byColor(opponent, sender)).start()
                            }
                        }
                    }
                }
            }
        }
    }

    fun onDisable() {
        ChessMatchManager.stop()
        coroutineScope.cancel()
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
