package gregc.gregchess

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.ChessClock
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.TimeUnit
import kotlin.contracts.ExperimentalContracts

@Suppress("unused")
class GregChess : JavaPlugin(), Listener {

    private val chess = ChessManager(this)

    private val drawRequest = RequestType<Unit>(
        RequestMessages(
            "Message.Request.Draw.Sent",
            "Message.Request.Draw.Cancelled",
            "Message.Request.Draw.Accepted",
            "Message.Request.Draw.NotFound",
            "Message.Request.Draw.AlreadySent",
            "/chess draw accept",
            "/chess draw cancel"
        )
    ) { (sender, _, _) ->
        chess.getGame(sender)?.stop(ChessGame.EndReason.DrawAgreement())
    }

    private val takebackRequest = RequestType<Unit>(
        RequestMessages(
            "Message.Request.Takeback.Sent",
            "Message.Request.Takeback.Cancelled",
            "Message.Request.Takeback.Accepted",
            "Message.Request.Takeback.NotFound",
            "Message.Request.Takeback.AlreadySent",
            "/chess undo accept",
            "/chess undo cancel"
        )
    ) { (sender, _, _) ->
        chess.getGame(sender)?.board?.undoLastMove()
    }

    @ExperimentalContracts
    override fun onEnable() {
        server.pluginManager.registerEvents(this, this)
        saveDefaultConfig()
        chess.start()
        addCommand("chess") { player, args ->
            commandRequireArgumentsMin(args, 1)
            when (args[0].toLowerCase()) {
                "duel" -> {
                    commandRequirePlayer(player)
                    commandRequireArguments(args, 2)
                    val opponent = GregChessInfo.server.getPlayer(args[1])
                    commandRequireNotNull(opponent, string("Message.Error.PlayerNotFound"))
                    chess.duel(player, opponent)
                }
                "stockfish" -> {
                    commandRequirePlayer(player)
                    commandRequireArguments(args, 1)
                    chess.stockfish(player)
                }
                "resign" -> {
                    commandRequirePlayer(player)
                    commandRequireArguments(args, 1)
                    val p = chess[player]
                    commandRequireNotNull(p, string("Message.Error.NotInGame.You"))
                    p.game.stop(ChessGame.EndReason.Resignation(!p.side))
                }
                "leave" -> {
                    commandRequirePlayer(player)
                    commandRequireArguments(args, 1)
                    chess.leave(player)
                }
                "draw" -> {
                    commandRequirePlayer(player)
                    commandRequireArguments(args, 2)
                    when (args[1].toLowerCase()) {
                        "offer" -> {
                            val p = chess[player]
                            commandRequireNotNull(p, string("Message.Error.NotInGame.You"))
                            if (!p.hasTurn())
                                throw CommandException(string("Message.Error.HasTurn.Opponent"))
                            val opponent = p.game[!p.side]
                            if (opponent !is ChessPlayer.Human)
                                throw CommandException(string("Message.Error.NotHuman.Opponent"))
                            drawRequest += Request(player, opponent.player, Unit)
                        }
                        "accept" -> drawRequest += player
                        "cancel" -> drawRequest -= player
                        else -> throw CommandException(string("Message.Error.WrongArgument"))
                    }
                }
                "capture" -> {
                    commandRequirePlayer(player)
                    commandRequirePermission(player, "greg-chess.debug")
                    commandRequireArgumentsGeneral(args, 1, 2)
                    val p = chess[player]
                    commandRequireNotNull(p, string("Message.Error.NotInGame.You"))
                    val pos = if (args.size == 1) {
                        p.game.board.renderer.getPos(Loc.fromLocation(player.location))
                    } else {
                        try {
                            ChessPosition.parseFromString(args[1])
                        } catch (e: IllegalArgumentException) {
                            throw CommandException(e.toString())
                        }
                    }
                    p.game.board[pos]?.capture()
                    p.game.board.updateMoves()
                }
                "spawn" -> {
                    commandRequirePlayer(player)
                    commandRequirePermission(player, "greg-chess.debug")
                    commandRequireArgumentsGeneral(args, 3, 4)
                    val game = chess.getGame(player)
                    commandRequireNotNull(game, string("Message.Error.NotInGame.You"))
                    try {
                        val square = if (args.size == 3)
                            game.board.getSquare(Loc.fromLocation(player.location))!!
                        else
                            game.board.getSquare(ChessPosition.parseFromString(args[3]))!!
                        val piece = ChessType.valueOf(args[2])
                        square.piece?.capture()
                        square.piece = ChessPiece(piece, ChessSide.valueOf(args[1]), square)
                        game.board.updateMoves()
                    } catch (e: Exception) {
                        throw CommandException(e.toString())
                    }
                }
                "move" -> {
                    commandRequirePlayer(player)
                    commandRequirePermission(player, "greg-chess.debug")
                    commandRequireArguments(args, 3)
                    val game = chess.getGame(player)
                    commandRequireNotNull(game, string("Message.Error.NotInGame.You"))
                    try {
                        game.board[ChessPosition.parseFromString(args[2])]?.capture()
                        game.board[ChessPosition.parseFromString(args[1])]
                            ?.move(game.board.getSquare(ChessPosition.parseFromString(args[2]))!!)
                        game.board.updateMoves()
                    } catch (e: IllegalArgumentException) {
                        throw CommandException(e.toString())
                    }
                }
                "skip" -> {
                    commandRequirePlayer(player)
                    commandRequirePermission(player, "greg-chess.debug")
                    commandRequireArguments(args, 1)
                    val game = chess.getGame(player)
                    commandRequireNotNull(game, string("Message.Error.NotInGame.You"))
                    game.nextTurn()
                }
                "load" -> {
                    commandRequirePlayer(player)
                    commandRequirePermission(player, "greg-chess.debug")
                    val game = chess.getGame(player)
                    commandRequireNotNull(game, string("Message.Error.NotInGame.You"))
                    game.board.setFromFEN(args.drop(1).joinToString(separator = " "))
                }
                "save" -> {
                    commandRequirePlayer(player)
                    val game = chess.getGame(player)
                    commandRequireNotNull(game, string("Message.Error.NotInGame.You"))
                    val message = TextComponent(string("Message.CopyFEN"))
                    message.clickEvent = ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, game.board.getFEN())
                    player.spigot().sendMessage(message)
                }
                "time" -> {
                    commandRequirePlayer(player)
                    commandRequirePermission(player, "greg-chess.debug")
                    commandRequireArguments(args, 4)
                    val game = chess.getGame(player)
                    commandRequireNotNull(game, string("Message.Error.NotInGame.You"))
                    val clock = game.getComponent(ChessClock::class)
                    commandRequireNotNull(clock, string("Message.Error.ClockNotFound"))
                    try {
                        val side = ChessSide.valueOf(args[1])
                        val time = TimeUnit.SECONDS.toMillis(args[3].toLong())
                        when (args[2].toLowerCase()) {
                            "add" -> clock.addTime(side, time)
                            "set" -> clock.setTime(side, time)
                            else -> throw CommandException(string("Message.Error.WrongArgument"))
                        }
                    } catch (e: IllegalArgumentException) {
                        throw CommandException(e.toString())
                    }
                }
                "uci" -> {
                    commandRequirePlayer(player)
                    commandRequirePermission(player, "greg-chess.debug")
                    commandRequireArgumentsMin(args, 2)
                    val game = chess.getGame(player)
                    commandRequireNotNull(game, string("Message.Error.NotInGame.You"))
                    val engine = game.players.mapNotNull { it as? ChessPlayer.Engine }.firstOrNull()
                    commandRequireNotNull(engine, string("Message.Error.EngineNotFound"))
                    try {
                        when (args[1].toLowerCase()) {
                            "set" -> {
                                commandRequireArgumentsMin(args, 3)
                                engine.engine.setOption(args[2], args.drop(3).joinToString(" "))
                            }
                            "send" -> engine.engine.sendCommand(args.drop(2).joinToString(" "))
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
                    chess.spectate(player, toSpectate)
                }
                "reload" -> {
                    commandRequirePermission(player, "greg-chess.debug")
                    commandRequireArgumentsMin(args, 1)
                    reloadConfig()
                    chess.reload()
                }
                "undo" -> {
                    commandRequirePlayer(player)
                    commandRequireArguments(args, 2)
                    when (args[1].toLowerCase()) {
                        "request" -> {
                            val p = chess[player]
                            commandRequireNotNull(p, string("Message.Error.NotInGame.You"))
                            if (p.game.board.lastMove == null)
                                throw CommandException(string("Message.Error.NothingToTakeback"))
                            val opponent = p.game[!p.side]
                            if (opponent !is ChessPlayer.Human)
                                throw CommandException(string("Message.Error.NotHuman.Opponent"))
                            if (p.hasTurn() && p.player != opponent.player)
                                throw CommandException(string("Message.Error.HasTurn.You"))
                            takebackRequest += Request(player, opponent.player, Unit)
                        }
                        "accept" -> takebackRequest += player
                        "cancel" -> takebackRequest -= player
                        else -> throw CommandException(string("Message.Error.WrongArgument"))
                    }
                }
                else -> throw CommandException(string("Message.Error.WrongArgument"))
            }
        }
        addCommandTab("chess") { s, args ->
            fun <T> ifPermission(vararg list: T) =
                if (s.hasPermission("greg-chess.debug")) list.map { it.toString() } else emptyList()

            when (args.size) {
                1 -> listOf("duel", "stockfish", "resign", "leave", "draw", "save", "spectate", "undo") +
                        ifPermission("capture", "spawn", "move", "skip", "load", "time", "uci", "reload")
                2 -> when (args[0]) {
                    "duel" -> null
                    "spawn" -> ifPermission(*ChessSide.values())
                    "time" -> ifPermission(*ChessSide.values())
                    "uci" -> ifPermission("set", "send")
                    "spectate" -> null
                    "draw" -> listOf("offer", "accept", "cancel")
                    "undo" -> listOf("request", "accept", "cancel")
                    else -> listOf()
                }
                3 -> when (args[0]) {
                    "spawn" -> ifPermission(*ChessType.values())
                    "time" -> ifPermission("add", "set")
                    else -> listOf()
                }
                else -> listOf()
            }?.filter { it.startsWith(args.last()) }
        }
    }

    override fun onDisable() {
        chess.stop()
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
        e.game.realPlayers.forEach {
            drawRequest.quietRemove(it)
            takebackRequest.quietRemove(it)
        }
    }
}
