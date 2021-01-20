package gregc.gregchess

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.ChessTimer
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.TimeUnit
import kotlin.contracts.ExperimentalContracts

@Suppress("unused")
class GregChess : JavaPlugin() {

    private val chess = ChessManager(this)

    @ExperimentalContracts
    override fun onEnable() {
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
                    commandRequireArguments(args, 1)
                    val p = chess[player]
                    commandRequireNotNull(p, string("Message.Error.NotInGame.You"))
                    p.wantsDraw = !p.wantsDraw
                }
                "capture" -> {
                    commandRequirePlayer(player)
                    commandRequirePermission(player, "greg-chess.debug")
                    commandRequireArgumentsGeneral(args, 1, 2)
                    val p = chess[player]
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
                    val game = chess.getGame(player)
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
                    val game = chess.getGame(player)
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
                    val game = chess.getGame(player)
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
                    chess.spectate(player, toSpectate)
                }
                "reload" -> {
                    commandRequirePermission(player, "greg-chess.debug")
                    commandRequireArgumentsMin(args, 1)
                    reloadConfig()
                    chess.reload()
                }
                else -> throw CommandException(string("Message.Error.WrongArgument"))
            }
        }
        addCommandTab("chess") { s, args ->
            fun <T> ifPermission(vararg list: T) =
                if (s.hasPermission("greg-chess.debug")) list.map { it.toString() } else emptyList()

            when (args.size) {
                1 -> listOf("duel", "stockfish", "resign", "leave", "draw", "save", "spectate") +
                        ifPermission("capture", "spawn", "move", "skip", "load", "time", "uci", "reload")
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

    override fun onDisable() {
        chess.stop()
    }
}
