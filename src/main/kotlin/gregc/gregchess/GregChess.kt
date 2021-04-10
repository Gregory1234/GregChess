package gregc.gregchess

import gregc.gregchess.chess.*
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.command.CommandSender
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.*
import kotlin.contracts.ExperimentalContracts

@Suppress("unused")
class GregChess : JavaPlugin(), Listener {

    private val drawRequest = RequestTypeBuilder<Unit>().messagesSimple(
        "Draw", "/chess draw", "/chess draw"
    ).validate { ChessManager[it]?.hasTurn() ?: false }.onAccept { (sender, _, _) ->
        ChessManager.getGame(sender)?.stop(ChessGame.EndReason.DrawAgreement())
    }.register()

    private val takebackRequest = RequestTypeBuilder<Unit>().messagesSimple(
        "Takeback", "/chess undo", "/chess undo"
    ).validate {
        val game = ChessManager.getGame(it) ?: return@validate false
        (game[!game.currentTurn] as? ChessPlayer.Human)?.player == it
    }.onAccept { (sender, _, _) ->
        ChessManager.getGame(sender)?.board?.undoLastMove()
    }.register()

    private val duelRequest =
        RequestTypeBuilder<ChessGame>().messagesSimple(
            "Duel", "/chess duel accept", "/chess duel cancel"
        ).print { it.settings.name }.onAccept { (sender, receiver, g) ->
            g.addPlayers {
                human(sender, ChessSide.WHITE, sender == receiver)
                human(receiver, ChessSide.BLACK, sender == receiver)
            }.start()
        }.onCancel { (_, _, t) ->
            t.arena.clear()
        }.register()

    @ExperimentalContracts
    override fun onEnable() {
        server.pluginManager.registerEvents(this, this)
        saveDefaultConfig()
        ChessManager.start()
        RequestManager.start()
        addCommand("chess") { player, args ->
            cArgs(args, 1)
            when (args[0].toLowerCase()) {
                "duel" -> {
                    cPlayer(player)
                    cArgs(args, 2)
                    cRequire(!ChessManager.isInGame(player), "InGame.You")
                    when (args[1].toLowerCase()) {
                        "accept" -> {
                            cArgs(args, 3, 3)
                            cWrongArgument {
                                duelRequest.accept(player, UUID.fromString(args[2]))
                            }
                        }
                        "cancel" -> {
                            cArgs(args, 3, 3)
                            cWrongArgument {
                                duelRequest.cancel(player, UUID.fromString(args[2]))
                            }
                        }
                        else -> {
                            cArgs(args, 2, 2)
                            val opponent = cServerPlayer(args[1])
                            cRequire(!ChessManager.isInGame(opponent), "InGame.Opponent")
                            ChessManager.duelMenu(player) { arena, settings ->
                                duelRequest += Request(player, opponent, ChessGame(arena, settings))
                            }
                        }
                    }
                }
                "stockfish" -> {
                    cPlayer(player)
                    cArgs(args, 1, 1)
                    cRequire(!ChessManager.isInGame(player), "InGame.You")
                    ChessManager.duelMenu(player) { arena, settings ->
                        ChessGame(arena, settings).addPlayers {
                            human(player, ChessSide.WHITE, false)
                            engine("stockfish", ChessSide.BLACK)
                        }.start()
                    }
                }
                "resign" -> {
                    cPlayer(player)
                    cArgs(args, 1, 1)
                    val p = cNotNull(ChessManager[player], "NotInGame.You")
                    p.game.stop(ChessGame.EndReason.Resignation(!p.side))
                }
                "leave" -> {
                    cPlayer(player)
                    cArgs(args, 1, 1)
                    ChessManager.leave(player)
                }
                "draw" -> {
                    cPlayer(player)
                    cArgs(args, 1, 1)
                    val p = cNotNull(ChessManager[player], "NotInGame.You")
                    val opponent: ChessPlayer.Human = cCast(p.opponent, "NotHuman.Opponent")
                    drawRequest.simpleCall(Request(player, opponent.player, Unit))
                }
                "capture" -> {
                    cPlayer(player)
                    cPerms(player, "greg-chess.debug")
                    cArgs(args, 1, 2)
                    val p = cNotNull(ChessManager[player], "NotInGame.You")
                    val pos = if (args.size == 1)
                        p.game.board.renderer.getPos(Loc.fromLocation(player.location))
                    else
                        cWrongArgument { ChessPosition.parseFromString(args[1]) }
                    p.game.board[pos]?.capture()
                    p.game.board.updateMoves()
                    player.sendMessage(ConfigManager.getString("Message.BoardOpDone"))
                }
                "spawn" -> {
                    cPlayer(player)
                    cPerms(player, "greg-chess.debug")
                    cArgs(args, 3, 4)
                    val game = cNotNull(ChessManager.getGame(player), "NotInGame.You")
                    cWrongArgument {
                        val square = if (args.size == 3)
                            game.board.getSquare(Loc.fromLocation(player.location))!!
                        else
                            game.board.getSquare(ChessPosition.parseFromString(args[3]))!!
                        val piece = ChessType.valueOf(args[2])
                        square.piece?.capture()
                        square.piece = ChessPiece(piece, ChessSide.valueOf(args[1]), square)
                        game.board.updateMoves()
                        player.sendMessage(ConfigManager.getString("Message.BoardOpDone"))
                    }
                }
                "move" -> {
                    cPlayer(player)
                    cPerms(player, "greg-chess.debug")
                    cArgs(args, 3, 3)
                    val game = cNotNull(ChessManager.getGame(player), "NotInGame.You")
                    cWrongArgument {
                        game.board[ChessPosition.parseFromString(args[2])]?.capture()
                        game.board[ChessPosition.parseFromString(args[1])]
                            ?.move(game.board.getSquare(ChessPosition.parseFromString(args[2]))!!)
                        game.board.updateMoves()
                        player.sendMessage(ConfigManager.getString("Message.BoardOpDone"))
                    }
                }
                "skip" -> {
                    cPlayer(player)
                    cPerms(player, "greg-chess.debug")
                    cArgs(args, 1, 1)
                    val game = cNotNull(ChessManager.getGame(player), "NotInGame.You")
                    game.nextTurn()
                    player.sendMessage(ConfigManager.getString("Message.SkippedTurn"))
                }
                "load" -> {
                    cPlayer(player)
                    cPerms(player, "greg-chess.debug")
                    val game = cNotNull(ChessManager.getGame(player), "NotInGame.You")
                    game.board.setFromFEN(
                        FEN.parseFromString(args.drop(1).joinToString(separator = " "))
                    )
                    player.sendMessage(ConfigManager.getString("Message.LoadedFEN"))
                }
                "save" -> {
                    cPlayer(player)
                    cArgs(args, 1, 1)
                    val game = cNotNull(ChessManager.getGame(player), "NotInGame.You")
                    val message = TextComponent(config.getString("Message.CopyFEN"))
                    message.clickEvent =
                        ClickEvent(
                            ClickEvent.Action.COPY_TO_CLIPBOARD,
                            game.board.getFEN().toString()
                        )
                    player.spigot().sendMessage(message)
                }
                "time" -> {
                    cPlayer(player)
                    cPerms(player, "greg-chess.debug")
                    cArgs(args, 4, 4)
                    val game = cNotNull(ChessManager.getGame(player), "NotInGame.You")
                    val clock = cNotNull(game.clock, "ClockNotFound")
                    cWrongArgument {
                        val side = ChessSide.valueOf(args[1])
                        val time = cNotNull(parseDuration(args[3]), "WrongArgument")
                        when (args[2].toLowerCase()) {
                            "add" -> clock.addTime(side, time)
                            "set" -> clock.setTime(side, time)
                            else -> cWrongArgument()
                        }
                        player.sendMessage(ConfigManager.getString("Message.TimeOpDone"))
                    }
                }
                "uci" -> {
                    cPlayer(player)
                    cPerms(player, "greg-chess.admin")
                    cArgs(args, 2)
                    val game = cNotNull(ChessManager.getGame(player), "NotInGame.You")
                    val engines = game.chessPlayers.filterIsInstance<ChessPlayer.Engine>()
                    val engine = cNotNull(engines.firstOrNull(), "EngineNotFound")
                    cWrongArgument {
                        when (args[1].toLowerCase()) {
                            "set" -> {
                                cArgs(args, 3)
                                engine.engine.setOption(args[2], args.drop(3).joinToString(" "))
                            }
                            "send" -> engine.engine.sendCommand(args.drop(2).joinToString(" "))
                            else -> cWrongArgument()
                        }
                        player.sendMessage(ConfigManager.getString("Message.EngineCommandSent"))
                    }
                }
                "spectate" -> {
                    cPlayer(player)
                    cArgs(args, 2, 2)
                    val toSpectate = cServerPlayer(args[1])
                    ChessManager.addSpectator(player, toSpectate)
                }
                "reload" -> {
                    cPerms(player, "greg-chess.admin")
                    cArgs(args, 1, 1)
                    reloadConfig()
                    ChessManager.reload()
                    player.sendMessage(ConfigManager.getString("Message.ConfigReloaded"))
                }
                "dev" -> {
                    cRequire(server.pluginManager.isPluginEnabled("DevHelpPlugin"), "WrongArgument")
                    cPerms(player, "greg-chess.admin")
                    cArgs(args, 1, 1)
                    server.dispatchCommand(player, "devhelp GregChess ${description.version}")
                }
                "undo" -> {
                    cPlayer(player)
                    cArgs(args, 1, 1)
                    val p = cNotNull(ChessManager[player], "NotInGame.You")
                    cNotNull(p.game.board.lastMove, "NothingToTakeback")
                    val opponent: ChessPlayer.Human = cCast(p.opponent, "NotHuman.Opponent")
                    takebackRequest.simpleCall(Request(player, opponent.player, Unit))
                }
                "debug" -> {
                    cPerms(player, "greg-chess.admin")
                    cArgs(args, 2, 2)
                    cWrongArgument {
                        glog.level = GregLogger.Level.valueOf(args[1])
                        player.sendMessage(ConfigManager.getString("Message.LevelSet"))
                    }
                }
                "info" -> {
                    cWrongArgument {
                        printInfo(player, args.drop(1).toTypedArray())
                    }
                }
                else -> cWrongArgument()
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
                    "time" -> ifPermission("greg-chess.admin", "add", "set")
                    else -> listOf()
                }
                else -> listOf()
            }?.filter { it.startsWith(args.last()) }
        }
    }

    @ExperimentalContracts
    private fun printInfo(player: CommandSender, args: Array<String>) {
        cArgs(args, 1)
        when (args[0].toLowerCase()) {
            "game" -> {
                val game: ChessGame = if (args.size == 2) {
                    cPlayer(player)
                    cNotNull(ChessManager.getGame(player), "NotInGame.You")
                } else {
                    cPerms(player, "greg-chess.info")
                    cArgs(args, 3, 3)
                    cNotNull(ChessManager[UUID.fromString(args[2])], "GameNotFound")
                }
                player.sendMessage(game.toString())
            }
            "piece" -> {
                if (args.size == 2) {
                    cPlayer(player)
                    val game = cNotNull(ChessManager.getGame(player), "NotInGame.You")
                    val piece = game.board[Loc.fromLocation(player.location)]
                    player.sendMessage(piece.toString())
                } else {
                    cArgs(args, 3, 3)
                    if (args[2].length == 2) {
                        cPlayer(player)
                        val game = cNotNull(ChessManager.getGame(player), "NotInGame.You")
                        val piece = game.board[ChessPosition.parseFromString(args[2])]
                        player.sendMessage(piece.toString())
                    } else {
                        cPerms(player, "greg-chess.info")
                        val game = cNotNull(
                            ChessManager.firstGame { UUID.fromString(args[2]) in it.board },
                            "GameNotFound"
                        )
                        val piece = game.board[UUID.fromString(args[2])]
                        player.sendMessage(piece.toString())
                    }
                }
            }
            else -> cWrongArgument()
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

    @EventHandler
    fun onInventoryClick(e: InventoryClickEvent) {
        val holder = e.inventory.holder
        if (holder is Screen.Holder<*>) {
            e.isCancelled = true
            if (!holder.finished)
                if (holder.applyEvent(InventoryPosition.fromIndex(e.slot)))
                    e.whoClicked.closeInventory()
        }
    }

    @EventHandler
    fun onInventoryClose(e: InventoryCloseEvent) {
        val holder = e.inventory.holder
        if (holder is Screen.Holder<*>) {
            if (!holder.finished)
                holder.cancel()
        }
    }
}
