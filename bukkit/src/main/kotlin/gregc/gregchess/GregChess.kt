package gregc.gregchess

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.GameEndEvent
import gregc.gregchess.chess.component.TurnEndEvent
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

object GregChess : Listener {
    class Plugin : JavaPlugin() {
        companion object {
            lateinit var INSTANCE: Plugin
                private set
        }

        init {
            INSTANCE = this
            Config.initBukkit { config }
        }

        override fun onEnable() {
            GregChess.onEnable()
        }

        override fun onDisable() {
            GregChess.onDisable()
        }
    }

    val plugin
        get() = Plugin.INSTANCE

    private val ErrorConfig.clockNotFound by ErrorConfig
    private val ErrorConfig.engineNotFound by ErrorConfig
    private val ErrorConfig.stockfishNotFound by ErrorConfig
    private val ErrorConfig.pieceNotFound by ErrorConfig
    private val ErrorConfig.gameNotFound by ErrorConfig
    private val ErrorConfig.nothingToTakeback by ErrorConfig

    private val MessageConfig.boardOpDone by MessageConfig
    private val MessageConfig.skippedTurn by MessageConfig
    private val MessageConfig.timeOpDone by MessageConfig
    private val MessageConfig.engineCommandSent by MessageConfig
    private val MessageConfig.loadedFEN by MessageConfig
    private val MessageConfig.configReloaded by MessageConfig
    private val MessageConfig.levelSet by MessageConfig

    private val drawRequest = RequestManager.register("Draw", "/chess draw", "/chess draw")

    private val takebackRequest = RequestManager.register("Takeback", "/chess undo", "/chess undo")

    private val duelRequest = RequestManager.register("Duel", "/chess duel accept", "/chess duel cancel")

    private fun CommandArgs.perms(c: String = latestArg().lowercase()) = cPerms(player, "greg-chess.chess.$c")

    fun onEnable() {
        registerEvents()
        plugin.saveDefaultConfig()
        with (plugin) {
            glog += JavaGregLogger(logger)
            if (File(dataFolder.absolutePath + "/logs").mkdir()) {
                val now = DateTimeFormatter.ofPattern("uuuu-MM-dd-HH-mm-ss").format(LocalDateTime.now())
                val file = File(dataFolder.absolutePath + "/logs/GregChess-$now.log")
                file.createNewFile()
                glog += FileGregLogger(file)
            }
        }
        BukkitChessGameManager.start()
        BukkitArenaManager.start()
        RequestManager.start()
        plugin.addCommand("chess") {
            when (nextArg().lowercase()) {
                "duel" -> {
                    cPlayer(player)
                    perms()
                    cRequire(!player.human.isInGame(), Config.error.youInGame)
                    when (nextArg().lowercase()) {
                        "accept" -> {
                            cWrongArgument {
                                duelRequest.accept(player, UUID.fromString(lastArg()))
                            }
                        }
                        "cancel" -> {
                            cWrongArgument {
                                duelRequest.cancel(player, UUID.fromString(lastArg()))
                            }
                        }
                        else -> {
                            endArgs()
                            val opponent = cServerPlayer(latestArg())
                            cRequire(!opponent.human.isInGame(), Config.error.opponentInGame)
                            interact {
                                val settings = player.openSettingsMenu()
                                if (settings != null) {
                                    val res = duelRequest.call(RequestData(player, opponent, settings.name))
                                    if (res == RequestResponse.ACCEPT) {
                                        ChessGame(BukkitTimeManager, BukkitArenaManager.cNext(), settings).addPlayers {
                                            human(player.human, Side.WHITE, player == opponent)
                                            human(opponent.human, Side.BLACK, player == opponent)
                                        }.start()
                                    }
                                }
                            }
                        }
                    }
                }
                "stockfish" -> {
                    cPlayer(player)
                    perms()
                    cRequire(Config.stockfish.hasStockfish, Config.error.stockfishNotFound)
                    endArgs()
                    cRequire(!player.human.isInGame(), Config.error.youInGame)
                    interact {
                        val settings = player.openSettingsMenu()
                        if (settings != null)
                            ChessGame(BukkitTimeManager, BukkitArenaManager.cNext(), settings).addPlayers {
                                human(player.human, Side.WHITE, false)
                                engine(Stockfish(), Side.BLACK)
                            }.start()
                    }
                }
                "resign" -> {
                    cPlayer(player)
                    perms()
                    endArgs()
                    val p = cNotNull(player.human.chess, Config.error.youNotInGame)
                    p.game.stop(EndReason.Resignation(!p.side))
                }
                "leave" -> {
                    cPlayer(player)
                    perms()
                    endArgs()
                    BukkitChessGameManager.leave(player.human)
                }
                "draw" -> {
                    cPlayer(player)
                    perms()
                    endArgs()
                    val p = cNotNull(player.human.chess, Config.error.youNotInGame)
                    val opponent: HumanChessPlayer = cCast(p.opponent, Config.error.opponentNotHuman)
                    interact {
                        drawRequest.invalidSender(player) { !p.hasTurn }
                        val res = drawRequest.call(RequestData(player, opponent.player.bukkit, ""), true)
                        if (res == RequestResponse.ACCEPT) {
                            p.game.stop(EndReason.DrawAgreement())
                        }
                    }
                }
                "capture" -> {
                    cPlayer(player)
                    perms()
                    val p = cNotNull(player.human.chess, Config.error.youNotInGame)
                    val pos = if (args.size == 1)
                        p.game.cRequireRenderer<Loc, Pos> { it.getPos(player.location.toLoc()) }
                    else
                        cWrongArgument { Pos.parseFromString(nextArg()) }
                    endArgs()
                    p.game.board[pos]?.piece?.capture(p.side)
                    p.game.board.updateMoves()
                    player.human.sendMessage(Config.message.boardOpDone)
                }
                "spawn" -> {
                    cPlayer(player)
                    perms()
                    cArgs(args, 3, 4)
                    val p = cNotNull(player.human.chess, Config.error.youNotInGame)
                    val game = p.game
                    cWrongArgument {
                        val square = if (args.size == 3)
                            game.board[player.location.toLoc()]!!
                        else
                            game.board[Pos.parseFromString(this[2])]!!
                        val piece = PieceType.valueOf(this[1])
                        square.piece?.capture(p.side)
                        square.piece = BoardPiece(Piece(piece, Side.valueOf(this[0])), square)
                        game.board.updateMoves()
                        player.human.sendMessage(Config.message.boardOpDone)
                    }
                }
                "move" -> {
                    cPlayer(player)
                    perms()
                    cArgs(args, 3, 3)
                    val p = cNotNull(player.human.chess, Config.error.youNotInGame)
                    val game = p.game
                    cWrongArgument {
                        game.board[Pos.parseFromString(this[2])]?.piece?.capture(p.side)
                        game.board[Pos.parseFromString(this[1])]?.piece?.move(game.board[Pos.parseFromString(this[2])]!!)
                        game.board.updateMoves()
                        player.human.sendMessage(Config.message.boardOpDone)
                    }
                }
                "skip" -> {
                    cPlayer(player)
                    perms()
                    endArgs()
                    val game = cNotNull(player.human.currentGame, Config.error.youNotInGame)
                    game.nextTurn()
                    player.human.sendMessage(Config.message.skippedTurn)
                }
                "load" -> {
                    cPlayer(player)
                    perms()
                    val game = cNotNull(player.human.currentGame, Config.error.youNotInGame)
                    game.board.setFromFEN(FEN.parseFromString(restString()))
                    player.human.sendMessage(Config.message.loadedFEN)
                }
                "save" -> {
                    cPlayer(player)
                    perms()
                    endArgs()
                    val game = cNotNull(player.human.currentGame, Config.error.youNotInGame)
                    player.human.sendFEN(game.board.getFEN())
                }
                "time" -> {
                    cPlayer(player)
                    perms()
                    cArgs(args, 4, 4)
                    val game = cNotNull(player.human.currentGame, Config.error.youNotInGame)
                    val clock = cNotNull(game.clock, Config.error.clockNotFound)
                    cWrongArgument {
                        val side = Side.valueOf(nextArg())
                        val time = cNotNull(parseDuration(this[1]), Config.error.wrongArgument)
                        when (nextArg().lowercase()) {
                            "add" -> clock.addTime(side, time)
                            "set" -> clock.setTime(side, time)
                            else -> cWrongArgument()
                        }
                        player.human.sendMessage(Config.message.timeOpDone)
                    }
                }
                "uci" -> {
                    cPlayer(player)
                    perms()
                    val game = cNotNull(player.human.currentGame, Config.error.youNotInGame)
                    val engines = game.players.toList().filterIsInstance<EnginePlayer>()
                    val engine = cNotNull(engines.firstOrNull(), Config.error.engineNotFound)
                    cWrongArgument {
                        when (nextArg().lowercase()) {
                            "set" -> {
                                engine.engine.setOption(nextArg(), restString())
                            }
                            "send" -> engine.engine.sendCommand(restString())
                            else -> cWrongArgument()
                        }
                        player.human.sendMessage(Config.message.engineCommandSent)
                    }
                }
                "spectate" -> {
                    cPlayer(player)
                    perms()
                    val toSpectate = cServerPlayer(lastArg())
                    player.human.spectatedGame = cNotNull(toSpectate.human.currentGame, Config.error.playerNotInGame)
                }
                "reload" -> {
                    perms()
                    endArgs()
                    plugin.reloadConfig()
                    BukkitArenaManager.reload()
                    player.sendMessage(Config.message.configReloaded.get(player.lang))
                }
                "dev" -> {
                    cRequire(Bukkit.getPluginManager().isPluginEnabled("DevHelpPlugin"), Config.error.wrongArgument)
                    perms()
                    endArgs()
                    Bukkit.dispatchCommand(player, "devhelp GregChess ${plugin.description.version}")
                }
                "undo" -> {
                    cPlayer(player)
                    perms()
                    endArgs()
                    val p = cNotNull(player.human.chess, Config.error.youNotInGame)
                    cNotNull(p.game.board.lastMove, Config.error.nothingToTakeback)
                    val opponent: HumanChessPlayer = cCast(p.opponent, Config.error.opponentNotHuman)
                    interact {
                        drawRequest.invalidSender(player) {
                            (p.game.currentOpponent as? HumanChessPlayer)?.player != player.human
                        }
                        val res = takebackRequest.call(RequestData(player, opponent.player.bukkit, ""), true)
                        if (res == RequestResponse.ACCEPT) {
                            p.game.board.undoLastMove()
                        }
                    }

                }
                "debug" -> {
                    perms()
                    cWrongArgument {
                        glog.level = GregLogger.Level.valueOf(lastArg())
                        player.sendMessage(Config.message.levelSet.get(player.lang))
                    }
                }
                "info" -> {
                    cWrongArgument {
                        when (nextArg().lowercase()) {
                            "game" -> player.spigot().sendMessage(selectGame().getInfo())
                            "piece" -> player.spigot().sendMessage(selectPiece().getInfo())
                            else -> cWrongArgument()
                        }
                    }
                }
                "admin" -> {
                    cPlayer(player)
                    perms()
                    endArgs()
                    player.human.isAdmin = !player.human.isAdmin
                }
                else -> cWrongArgument()
            }
        }
        plugin.addCommandTab("chess") {
            fun ifPermission(perm: String): List<String>? =
                if (player.hasPermission("greg-chess.chess.$perm")) null else emptyList()
            fun <T> ifPermission(perm: String, list: Array<T>) =
                if (player.hasPermission("greg-chess.chess.$perm")) list.map { it.toString() } else emptyList()
            fun <T> ifPermissionPrefix(vararg list: T) =
                list.map { it.toString() }.filter { player.hasPermission("greg-chess.chess.$it") }
            fun <T> ifInfo(vararg list: T) =
                if (player.hasPermission("greg-chess.chess.info.ingame") || player.hasPermission("greg-chess.info.chess.remote")) list.map { it.toString() } else emptyList()

            when (args.size) {
                1 -> ifPermissionPrefix("duel", "stockfish", "resign", "leave", "draw", "capture", "spawn", "move",
                    "skip", "load", "save", "time", "uci", "spectate", "reload", "dev", "undo", "debug", "admin") +
                        ifInfo("info")
                2 -> when (args[0]) {
                    "duel" -> ifPermission("duel")
                    "spawn" -> ifPermission("spawn", Side.values())
                    "time" -> ifPermission("time", Side.values())
                    "uci" -> ifPermission("uci", arrayOf("set", "send"))
                    "spectate" -> ifPermission("spectate")
                    "info" -> ifInfo("game", "piece")
                    else -> listOf()
                }
                3 -> when (args[0]) {
                    "spawn" -> ifPermission("spawn", PieceType.values())
                    "time" -> ifPermission("time", arrayOf("add", "set"))
                    else -> listOf()
                }
                else -> listOf()
            }?.filter { it.startsWith(args.last()) }
        }
    }

    private fun CommandArgs.selectPiece() =
        when (rest().size) {
            0 -> {
                cPlayer(player)
                perms("info.ingame")
                val game = cNotNull(player.human.currentGame, Config.error.youNotInGame)
                cNotNull(game.board[player.location.toLoc()]?.piece, Config.error.pieceNotFound)
            }
            1 -> {
                if (isValidUUID(nextArg())) {
                    perms("info.remote")
                    val game = cNotNull(
                        BukkitChessGameManager.firstGame { UUID.fromString(latestArg()) in it.board },
                        Config.error.pieceNotFound
                    )
                    game.board[UUID.fromString(latestArg())]!!
                } else {
                    cPlayer(player)
                    perms("info.ingame")
                    val game = cNotNull(player.human.currentGame, Config.error.youNotInGame)
                    cNotNull(game.board[Pos.parseFromString(latestArg())]?.piece, Config.error.pieceNotFound)
                }
            }
            else -> throw CommandException(Config.error.wrongArgumentsNumber)
        }

    private fun CommandArgs.selectGame() =
        when (rest().size) {
            0 -> {
                cPlayer(player)
                perms("info.ingame")
                cNotNull(player.human.currentGame, Config.error.youNotInGame)
            }
            1 -> {
                cWrongArgument {
                    perms("info.remote")
                    cNotNull(BukkitChessGameManager[UUID.fromString(nextArg())], Config.error.gameNotFound)
                }
            }
            else -> throw CommandException(Config.error.wrongArgumentsNumber)
        }

    fun onDisable() {
        BukkitChessGameManager.stop()
    }

    @EventHandler
    fun onTurnEnd(e: TurnEndEvent) {
        if (e.player is HumanChessPlayer) {
            drawRequest.quietRemove(e.player.player.bukkit)
            takebackRequest.quietRemove(e.player.player.bukkit)
        }
    }

    @EventHandler
    fun onGameEnd(e: GameEndEvent) {
        e.game.players.forEachReal {
            drawRequest.quietRemove(it.bukkit)
            takebackRequest.quietRemove(it.bukkit)
        }
    }

    @EventHandler
    fun onInventoryClick(e: InventoryClickEvent) {
        val holder = e.inventory.holder
        if (holder is BukkitMenu<*>) {
            e.isCancelled = true
            cTry(e.whoClicked, { e.whoClicked.closeInventory() }) {
                if (!holder.finished)
                    if (holder.click(InventoryPosition.fromIndex(e.slot)))
                        e.whoClicked.closeInventory()
            }
        }
    }

    @EventHandler
    fun onInventoryClose(e: InventoryCloseEvent) {
        val holder = e.inventory.holder
        if (holder is BukkitMenu<*>) {
            cTry(e.player) {
                if (!holder.finished)
                    holder.cancel()
            }
        }
    }
}
