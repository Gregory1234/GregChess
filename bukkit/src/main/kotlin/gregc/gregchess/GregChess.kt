package gregc.gregchess

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.GameEndEvent
import gregc.gregchess.chess.component.TurnEndEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@Suppress("unused")
class GregChess : JavaPlugin(), Listener {

    private val arenaManager = BukkitArenaManager(this)
    private val timeManager = BukkitTimeManager(this)
    private val requestManager = BukkitRequestManager(this, timeManager)
    private val chessManager = BukkitChessGameManager(this)

    init {
        Config.initBukkit { config }
    }

    private val drawRequest = requestManager.register("draw", "/chess draw", "/chess draw")

    private val takebackRequest = requestManager.register("takeback", "/chess undo", "/chess undo")

    private val duelRequest = requestManager.register("duel", "/chess duel accept", "/chess duel cancel")

    override fun onEnable() {
        server.pluginManager.registerEvents(this, this)
        saveDefaultConfig()
        run {
            glog += JavaGregLogger(logger)
            if (File(dataFolder.absolutePath + "/logs").mkdir()) {
                val now = DateTimeFormatter.ofPattern("uuuu-MM-dd-HH-mm-ss").format(LocalDateTime.now())
                val file = File(dataFolder.absolutePath + "/logs/GregChess-$now.log")
                file.createNewFile()
                glog += FileGregLogger(file)
            }
        }
        chessManager.start()
        arenaManager.start()
        requestManager.start()
        addCommand("chess") {
            when (nextArg().lowercase()) {
                "duel" -> {
                    cPlayer(player)
                    cRequire(!player.human.isInGame(), Config.error.youInGame)
                    when (nextArg().lowercase()) {
                        "accept" -> {
                            cWrongArgument {
                                duelRequest.accept(player.human, UUID.fromString(lastArg()))
                            }
                        }
                        "cancel" -> {
                            cWrongArgument {
                                duelRequest.cancel(player.human, UUID.fromString(lastArg()))
                            }
                        }
                        else -> {
                            endArgs()
                            val opponent = cServerPlayer(latestArg())
                            cRequire(!opponent.human.isInGame(), Config.error.opponentInGame)
                            interact {
                                val settings = player.openSettingsMenu()
                                if (settings != null) {
                                    val res = duelRequest.call(RequestData(player.human, opponent.human, settings.name))
                                    if (res == RequestResponse.ACCEPT) {
                                        ChessGame(timeManager, arenaManager.cNext(), settings).addPlayers {
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
                    cRequire(Config.stockfish.hasStockfish, Config.error.stockfishNotFound)
                    endArgs()
                    cRequire(!player.human.isInGame(), Config.error.youInGame)
                    interact {
                        val settings = player.openSettingsMenu()
                        if (settings != null)
                            ChessGame(timeManager, arenaManager.cNext(), settings).addPlayers {
                                human(player.human, Side.WHITE, false)
                                engine(Stockfish(timeManager), Side.BLACK)
                            }.start()
                    }
                }
                "resign" -> {
                    cPlayer(player)
                    endArgs()
                    val p = cNotNull(player.human.chess, Config.error.youNotInGame)
                    p.game.stop(EndReason.Resignation(!p.side))
                }
                "leave" -> {
                    cPlayer(player)
                    endArgs()
                    chessManager.leave(player.human)
                }
                "draw" -> {
                    cPlayer(player)
                    endArgs()
                    val p = cNotNull(player.human.chess, Config.error.youNotInGame)
                    val opponent: HumanChessPlayer = cCast(p.opponent, Config.error.opponentNotHuman)
                    interact {
                        drawRequest.invalidSender(player.human) { !p.hasTurn }
                        val res = drawRequest.call(RequestData(player.human, opponent.player, ""), true)
                        if (res == RequestResponse.ACCEPT) {
                            p.game.stop(EndReason.DrawAgreement())
                        }
                    }
                }
                "capture" -> {
                    cPlayer(player)
                    cPerms(player, "greg-chess.debug")
                    val p = cNotNull(player.human.chess, Config.error.youNotInGame)
                    val pos = if (args.size == 1)
                        p.game.cRequireRenderer<Loc, Pos> { it.getPos(player.location.toLoc()) }
                    else
                        cWrongArgument { Pos.parseFromString(nextArg()) }
                    endArgs()
                    p.game.board[pos]?.piece?.capture(p.side)
                    p.game.board.updateMoves()
                    player.sendMessage(Config.message.boardOpDone)
                }
                "spawn" -> {
                    cPlayer(player)
                    cPerms(player, "greg-chess.debug")
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
                        player.sendMessage(Config.message.boardOpDone)
                    }
                }
                "move" -> {
                    cPlayer(player)
                    cPerms(player, "greg-chess.debug")
                    cArgs(args, 3, 3)
                    val p = cNotNull(player.human.chess, Config.error.youNotInGame)
                    val game = p.game
                    cWrongArgument {
                        game.board[Pos.parseFromString(this[2])]?.piece?.capture(p.side)
                        game.board[Pos.parseFromString(this[1])]?.piece?.move(game.board[Pos.parseFromString(this[2])]!!)
                        game.board.updateMoves()
                        player.sendMessage(Config.message.boardOpDone)
                    }
                }
                "skip" -> {
                    cPlayer(player)
                    cPerms(player, "greg-chess.debug")
                    endArgs()
                    val game = cNotNull(player.human.currentGame, Config.error.youNotInGame)
                    game.nextTurn()
                    player.sendMessage(Config.message.skippedTurn)
                }
                "load" -> {
                    cPlayer(player)
                    cPerms(player, "greg-chess.debug")
                    val game = cNotNull(player.human.currentGame, Config.error.youNotInGame)
                    game.board.setFromFEN(FEN.parseFromString(restString()))
                    player.sendMessage(Config.message.loadedFEN)
                }
                "save" -> {
                    cPlayer(player)
                    endArgs()
                    val game = cNotNull(player.human.currentGame, Config.error.youNotInGame)
                    player.human.sendFEN(game.board.getFEN())
                }
                "time" -> {
                    cPlayer(player)
                    cPerms(player, "greg-chess.debug")
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
                        player.sendMessage(Config.message.timeOpDone)
                    }
                }
                "uci" -> {
                    cPlayer(player)
                    cPerms(player, "greg-chess.admin")
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
                        player.sendMessage(Config.message.engineCommandSent)
                    }
                }
                "spectate" -> {
                    cPlayer(player)
                    val toSpectate = cServerPlayer(lastArg())
                    player.human.spectatedGame = cNotNull(toSpectate.human.currentGame, Config.error.playerNotInGame)
                }
                "reload" -> {
                    cPerms(player, "greg-chess.admin")
                    endArgs()
                    reloadConfig()
                    arenaManager.reload()
                    player.sendMessage(Config.message.configReloaded)
                }
                "dev" -> {
                    cRequire(server.pluginManager.isPluginEnabled("DevHelpPlugin"), Config.error.wrongArgument)
                    cPerms(player, "greg-chess.admin")
                    endArgs()
                    server.dispatchCommand(player, "devhelp GregChess ${description.version}")
                }
                "undo" -> {
                    cPlayer(player)
                    endArgs()
                    val p = cNotNull(player.human.chess, Config.error.youNotInGame)
                    cNotNull(p.game.board.lastMove, Config.error.nothingToTakeback)
                    val opponent: HumanChessPlayer = cCast(p.opponent, Config.error.opponentNotHuman)
                    interact {
                        drawRequest.invalidSender(player.human) {
                            (p.game.currentOpponent as? HumanChessPlayer)?.player != player.human
                        }
                        val res = takebackRequest.call(RequestData(player.human, opponent.player, ""), true)
                        if (res == RequestResponse.ACCEPT) {
                            p.game.board.undoLastMove()
                        }
                    }

                }
                "debug" -> {
                    cPerms(player, "greg-chess.admin")
                    cWrongArgument {
                        glog.level = GregLogger.Level.valueOf(lastArg())
                        player.sendMessage(Config.message.levelSet)
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
                    cPerms(player, "greg-chess.admin")
                    endArgs()
                    player.human.isAdmin = !player.human.isAdmin
                }
                else -> cWrongArgument()
            }
        }
        addCommandTab("chess") {
            fun <T> ifPermission(perm: String, vararg list: T) =
                if (player.hasPermission(perm)) list.map { it.toString() } else emptyList()

            when (args.size) {
                1 -> listOf(
                    "duel", "stockfish", "resign", "leave", "draw", "save", "spectate",
                    "undo", "info"
                ) + ifPermission(
                    "greg-chess.debug", "capture", "spawn", "move", "skip", "load", "time"
                ) + ifPermission(
                    "greg-chess.admin", "uci", "reload", "dev", "debug", "admin"
                )
                2 -> when (args[0]) {
                    "duel" -> null
                    "spawn" -> ifPermission("greg-chess.debug", *Side.values())
                    "time" -> ifPermission("greg-chess.debug", *Side.values())
                    "uci" -> ifPermission("greg-chess.admin", "set", "send")
                    "spectate" -> null
                    "info" -> listOf("game", "piece")
                    else -> listOf()
                }
                3 -> when (args[0]) {
                    "spawn" -> ifPermission("greg-chess.debug", *PieceType.values())
                    "time" -> ifPermission("greg-chess.admin", "add", "set")
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
                val game = cNotNull(player.human.currentGame, Config.error.youNotInGame)
                cNotNull(game.board[player.location.toLoc()]?.piece, Config.error.pieceNotFound)
            }
            1 -> {
                if (isValidUUID(nextArg())) {
                    cPerms(player, "greg-chess.info")
                    val game = cNotNull(
                        chessManager.firstGame { UUID.fromString(latestArg()) in it.board },
                        Config.error.pieceNotFound
                    )
                    game.board[UUID.fromString(latestArg())]!!
                } else {
                    cPlayer(player)
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
                cNotNull(player.human.currentGame, Config.error.youNotInGame)
            }
            1 -> {
                cWrongArgument {
                    cPerms(player, "greg-chess.info")
                    cNotNull(chessManager[UUID.fromString(nextArg())], Config.error.gameNotFound)
                }
            }
            else -> throw CommandException(Config.error.wrongArgumentsNumber)
        }

    override fun onDisable() {
        chessManager.stop()
    }

    @EventHandler
    fun onTurnEnd(e: TurnEndEvent) {
        if (e.player is HumanChessPlayer) {
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