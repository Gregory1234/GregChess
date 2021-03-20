package gregc.gregchess

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.ChessClock
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.*
import kotlin.contracts.ExperimentalContracts

@Suppress("unused")
class GregChess : JavaPlugin(), Listener {

    private val drawRequest = RequestTypeBuilder<Unit>().messagesSimple(
        "Draw",
        "/chess draw",
        "/chess draw"
    ).validate { ChessManager[it]?.hasTurn() ?: false }.onAccept { (sender, _, _) ->
        ChessManager.getGame(sender)?.stop(ChessGame.EndReason.DrawAgreement())
    }.register()

    private val takebackRequest = RequestTypeBuilder<Unit>().messagesSimple(
        "Takeback",
        "/chess undo",
        "/chess undo"
    ).validate {
        val game = ChessManager.getGame(it) ?: return@validate false
        (game[!game.currentTurn] as? ChessPlayer.Human)?.player == it
    }.onAccept { (sender, _, _) ->
        ChessManager.getGame(sender)?.board?.undoLastMove()
    }.register()

    private val duelRequest =
        RequestTypeBuilder<Pair<ChessArena, ChessGame.Settings>>().messagesSimple(
            "Duel",
            "/chess duel accept",
            "/chess duel cancel"
        ).print { (_, settings) -> settings.name }.onAccept { (sender, receiver, t) ->
            ChessManager.startDuel(sender, receiver, t.first, t.second)
        }.onCancel { (_, _, t) ->
            t.first.clear()
        }.register()

    @ExperimentalContracts
    override fun onEnable() {
        try {
            File(dataFolder.absolutePath + "/GregChess.log").renameTo(File(dataFolder.absolutePath + "/backup.log"))
        } catch (e: Exception) {

        }
        server.pluginManager.registerEvents(this, this)
        saveDefaultConfig()
        ChessManager.start()
        RequestManager.start()
        addCommand("chess") { player, args ->
            commandRequireArgumentsMin(args, 1)
            when (args[0].toLowerCase()) {
                "duel" -> {
                    commandRequirePlayer(player)
                    commandRequireArgumentsMin(args, 2)
                    when (args[1].toLowerCase()) {
                        "accept" -> {
                            commandRequireArguments(args, 3)
                            try {
                                duelRequest.accept(player, UUID.fromString(args[2]))
                            } catch (e: IllegalArgumentException) {
                                throw CommandException("WrongArgument")
                            }
                        }
                        "cancel" -> {
                            commandRequireArguments(args, 3)
                            try {
                                duelRequest.cancel(player, UUID.fromString(args[2]))
                            } catch (e: IllegalArgumentException) {
                                throw CommandException("WrongArgument")
                            }
                        }
                        else -> {
                            commandRequireArguments(args, 2)
                            val opponent = GregInfo.server.getPlayer(args[1])
                            commandRequireNotNull(opponent, "PlayerNotFound")
                            ChessManager.duelMenu(player, opponent) { arena, settings ->
                                duelRequest += Request(player, opponent, Pair(arena, settings))
                            }
                        }
                    }
                }
                "stockfish" -> {
                    commandRequirePlayer(player)
                    commandRequireArguments(args, 1)
                    ChessManager.stockfish(player)
                }
                "resign" -> {
                    commandRequirePlayer(player)
                    commandRequireArguments(args, 1)
                    val p = ChessManager[player]
                    commandRequireNotNull(p, "NotInGame.You")
                    p.game.stop(ChessGame.EndReason.Resignation(!p.side))
                }
                "leave" -> {
                    commandRequirePlayer(player)
                    commandRequireArguments(args, 1)
                    ChessManager.leave(player)
                }
                "draw" -> {
                    commandRequirePlayer(player)
                    commandRequireArguments(args, 1)
                    val p = ChessManager[player]
                    commandRequireNotNull(p, "NotInGame.You")
                    val opponent = p.game[!p.side]
                    if (opponent !is ChessPlayer.Human)
                        throw CommandException("NotHuman.Opponent")
                    drawRequest.simpleCall(Request(player, opponent.player, Unit))
                }
                "capture" -> {
                    commandRequirePlayer(player)
                    commandRequirePermission(player, "greg-chess.debug")
                    commandRequireArgumentsGeneral(args, 1, 2)
                    val p = ChessManager[player]
                    commandRequireNotNull(p, "NotInGame.You")
                    val pos = if (args.size == 1) {
                        p.game.board.renderer.getPos(Loc.fromLocation(player.location))
                    } else {
                        try {
                            ChessPosition.parseFromString(args[1])
                        } catch (e: IllegalArgumentException) {
                            e.printStackTrace()
                            throw CommandException("WrongArgument")
                        }
                    }
                    p.game.board[pos]?.capture()
                    p.game.board.updateMoves()
                    player.sendMessage(ConfigManager.getString("Message.BoardOpDone"))
                }
                "spawn" -> {
                    commandRequirePlayer(player)
                    commandRequirePermission(player, "greg-chess.debug")
                    commandRequireArgumentsGeneral(args, 3, 4)
                    val game = ChessManager.getGame(player)
                    commandRequireNotNull(game, "NotInGame.You")
                    try {
                        val square = if (args.size == 3)
                            game.board.getSquare(Loc.fromLocation(player.location))!!
                        else
                            game.board.getSquare(ChessPosition.parseFromString(args[3]))!!
                        val piece = ChessType.valueOf(args[2])
                        square.piece?.capture()
                        square.piece = ChessPiece(piece, ChessSide.valueOf(args[1]), square)
                        game.board.updateMoves()
                        player.sendMessage(ConfigManager.getString("Message.BoardOpDone"))
                    } catch (e: Exception) {
                        e.printStackTrace()
                        throw CommandException("WrongArgument")
                    }
                }
                "move" -> {
                    commandRequirePlayer(player)
                    commandRequirePermission(player, "greg-chess.debug")
                    commandRequireArguments(args, 3)
                    val game = ChessManager.getGame(player)
                    commandRequireNotNull(game, "NotInGame.You")
                    try {
                        game.board[ChessPosition.parseFromString(args[2])]?.capture()
                        game.board[ChessPosition.parseFromString(args[1])]
                            ?.move(game.board.getSquare(ChessPosition.parseFromString(args[2]))!!)
                        game.board.updateMoves()
                        player.sendMessage(ConfigManager.getString("Message.BoardOpDone"))
                    } catch (e: IllegalArgumentException) {
                        e.printStackTrace()
                        throw CommandException("WrongArgument")
                    }
                }
                "skip" -> {
                    commandRequirePlayer(player)
                    commandRequirePermission(player, "greg-chess.debug")
                    commandRequireArguments(args, 1)
                    val game = ChessManager.getGame(player)
                    commandRequireNotNull(game, "NotInGame.You")
                    game.nextTurn()
                    player.sendMessage(ConfigManager.getString("Message.SkippedTurn"))
                }
                "load" -> {
                    commandRequirePlayer(player)
                    commandRequirePermission(player, "greg-chess.debug")
                    val game = ChessManager.getGame(player)
                    commandRequireNotNull(game, "NotInGame.You")
                    game.board.setFromFEN(args.drop(1).joinToString(separator = " "))
                    player.sendMessage(ConfigManager.getString("Message.LoadedFEN"))
                }
                "save" -> {
                    commandRequirePlayer(player)
                    val game = ChessManager.getGame(player)
                    commandRequireNotNull(game, "NotInGame.You")
                    val message = TextComponent(config.getString("Message.CopyFEN"))
                    message.clickEvent =
                        ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, game.board.getFEN())
                    player.spigot().sendMessage(message)
                }
                "time" -> {
                    commandRequirePlayer(player)
                    commandRequirePermission(player, "greg-chess.debug")
                    commandRequireArguments(args, 4)
                    val game = ChessManager.getGame(player)
                    commandRequireNotNull(game, "NotInGame.You")
                    val clock = game.getComponent(ChessClock::class)
                    commandRequireNotNull(clock, "ClockNotFound")
                    try {
                        val side = ChessSide.valueOf(args[1])
                        val time = parseDuration(args[3])
                        commandRequireNotNull(time, "WrongArgument")
                        when (args[2].toLowerCase()) {
                            "add" -> clock.addTime(side, time)
                            "set" -> clock.setTime(side, time)
                            else -> throw CommandException("WrongArgument")
                        }
                        player.sendMessage(ConfigManager.getString("Message.TimeOpDone"))
                    } catch (e: IllegalArgumentException) {
                        e.printStackTrace()
                        throw CommandException("WrongArgument")
                    }
                }
                "uci" -> {
                    commandRequirePlayer(player)
                    commandRequirePermission(player, "greg-chess.admin")
                    commandRequireArgumentsMin(args, 2)
                    val game = ChessManager.getGame(player)
                    commandRequireNotNull(game, "NotInGame.You")
                    val engine = game.players.mapNotNull { it as? ChessPlayer.Engine }.firstOrNull()
                    commandRequireNotNull(engine, "EngineNotFound")
                    try {
                        when (args[1].toLowerCase()) {
                            "set" -> {
                                commandRequireArgumentsMin(args, 3)
                                engine.engine.setOption(args[2], args.drop(3).joinToString(" "))
                            }
                            "send" -> engine.engine.sendCommand(args.drop(2).joinToString(" "))
                            else -> throw CommandException("WrongArgument")
                        }
                        player.sendMessage(ConfigManager.getString("Message.EngineCommandSent"))
                    } catch (e: IllegalArgumentException) {
                        e.printStackTrace()
                        throw CommandException("WrongArgument")
                    }
                }
                "spectate" -> {
                    commandRequirePlayer(player)
                    commandRequireArguments(args, 2)
                    val toSpectate = GregInfo.server.getPlayer(args[1])
                    commandRequireNotNull(toSpectate, "PlayerNotFound")
                    ChessManager.addSpectator(player, toSpectate)
                }
                "reload" -> {
                    commandRequirePermission(player, "greg-chess.admin")
                    commandRequireArguments(args, 1)
                    reloadConfig()
                    ChessManager.reload()
                    player.sendMessage(ConfigManager.getString("Message.ConfigReloaded"))
                }
                "dev" -> {
                    if (!server.pluginManager.isPluginEnabled("DevHelpPlugin"))
                        throw CommandException("WrongArgument")
                    commandRequirePermission(player, "greg-chess.admin")
                    commandRequireArguments(args, 1)
                    server.dispatchCommand(player, "devhelp GregChess ${description.version}")
                }
                "undo" -> {
                    commandRequirePlayer(player)
                    commandRequireArguments(args, 1)
                    val p = ChessManager[player]
                    commandRequireNotNull(p, "NotInGame.You")
                    if (p.game.board.lastMove == null)
                        throw CommandException("NothingToTakeback")
                    val opponent = p.game[!p.side]
                    if (opponent !is ChessPlayer.Human)
                        throw CommandException("NotHuman.Opponent")
                    takebackRequest.simpleCall(Request(player, opponent.player, Unit))
                }
                "debug" -> {
                    commandRequirePermission(player, "greg-chess.admin")
                    commandRequireArguments(args, 2)
                    try {
                        glog.level = GregLogger.Level.valueOf(args[1])
                        player.sendMessage(ConfigManager.getString("Message.LevelSet"))
                    } catch (e: IllegalArgumentException) {
                        e.printStackTrace()
                        throw CommandException("WrongArgument")
                    }
                }
                "info" -> {
                    commandRequireArgumentsMin(args, 2)
                    try {
                        when (args[1].toLowerCase()) {
                            "game" -> {
                                val game: ChessGame?
                                if (args.size == 2) {
                                    commandRequirePlayer(player)
                                    game = ChessManager.getGame(player)
                                    commandRequireNotNull(game, "NotInGame.You")
                                } else {
                                    commandRequirePermission(player, "greg-chess.info")
                                    commandRequireArguments(args, 3)
                                    game = ChessManager[UUID.fromString(args[2])]
                                    commandRequireNotNull(game, "GameNotFound")
                                }
                                player.sendMessage(game.toString())
                            }
                            "piece" -> {
                                if (args.size == 2) {
                                    commandRequirePlayer(player)
                                    val game = ChessManager.getGame(player)
                                    commandRequireNotNull(game, "NotInGame.You")
                                    val piece = game.board[Loc.fromLocation(player.location)]
                                    player.sendMessage(piece.toString())
                                } else {
                                    commandRequireArguments(args, 3)
                                    if (args[2].length == 2) {
                                        commandRequirePlayer(player)
                                        val game = ChessManager.getGame(player)
                                        commandRequireNotNull(game, "NotInGame.You")
                                        val piece =
                                            game.board[ChessPosition.parseFromString(args[2])]
                                        player.sendMessage(piece.toString())
                                    } else {
                                        commandRequirePermission(player, "greg-chess.info")
                                        val game =
                                            ChessManager.firstGame { UUID.fromString(args[2]) in it.board }
                                        commandRequireNotNull(game, "GameNotFound")
                                        val piece =
                                            game.board[UUID.fromString(args[2])]
                                        player.sendMessage(piece.toString())
                                    }
                                }
                            }
                            else -> throw CommandException("WrongArgument")
                        }
                    } catch (e: IllegalArgumentException) {
                        e.printStackTrace()
                        throw CommandException("WrongArgument")
                    }
                }
                else -> throw CommandException("WrongArgument")
            }
        }
        addCommandTab("chess") { s, args ->
            fun <T> ifPermission(perm: String, vararg list: T) =
                if (s.hasPermission(perm)) list.map { it.toString() } else emptyList()

            when (args.size) {
                1 -> listOf(
                    "duel", "stockfish", "resign", "leave", "draw", "save", "spectate",
                    "undo", "info"
                ) + ifPermission(
                    "greg-chess.debug", "capture", "spawn", "move", "skip", "load", "time"
                ) + ifPermission(
                    "greg-chess.admin", "uci", "reload", "dev", "debug"
                )
                2 -> when (args[0]) {
                    "duel" -> null
                    "spawn" -> ifPermission("greg-chess.debug", *ChessSide.values())
                    "time" -> ifPermission("greg-chess.debug", *ChessSide.values())
                    "uci" -> ifPermission("greg-chess.admin", "set", "send")
                    "spectate" -> null
                    "info" -> listOf("game", "piece")
                    else -> listOf()
                }
                3 -> when (args[0]) {
                    "spawn" -> ifPermission("greg-chess.debug", *ChessType.values())
                    "time" -> ifPermission("add", "set")
                    else -> listOf()
                }
                else -> listOf()
            }?.filter { it.startsWith(args.last()) }
        }
    }

    override fun onDisable() {
        ChessManager.stop()
    }

    @EventHandler
    fun onTurnEnd(e: ChessGame.TurnEndEvent) {
        if (e.player is ChessPlayer.Human) {
            drawRequest.quietRemove(e.player.player)
            takebackRequest.quietRemove(e.player.player)
        }
    }

    @EventHandler
    fun onGameEnd(e: ChessGame.EndEvent) {
        e.game.forEachPlayer {
            drawRequest.quietRemove(it)
            takebackRequest.quietRemove(it)
        }
    }
}
