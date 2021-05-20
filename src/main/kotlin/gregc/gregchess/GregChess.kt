package gregc.gregchess

import gregc.gregchess.chess.*
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

@Suppress("unused")
class GregChess : JavaPlugin(), Listener {

    private val drawRequest = buildRequestType<Unit> {
        messagesSimple("Draw", "/chess draw", "/chess draw")
        validateSender = { ChessManager[it]?.hasTurn ?: false }
        onAccept = { (sender, _, _) -> ChessManager.getGame(sender)?.stop(ChessGame.EndReason.DrawAgreement())}
    }

    private val takebackRequest = buildRequestType<Unit> {
        messagesSimple("Takeback", "/chess undo", "/chess undo")
        validateSender = { (ChessManager.getGame(it)?.currentPlayer?.opponent as? BukkitChessPlayer)?.player == it }
        onAccept = { (sender, _, _) -> ChessManager.getGame(sender)?.board?.undoLastMove() }
    }

    private val duelRequest = buildRequestType<ChessGame>{
        messagesSimple("Duel", "/chess duel accept", "/chess duel cancel")
        printT = { it.settings.name }
        onAccept = { (sender, receiver, g) ->
            g.addPlayers {
                human(sender, ChessSide.WHITE, sender == receiver)
                human(receiver, ChessSide.BLACK, sender == receiver)
            }.start()
        }
    }

    override fun onEnable() {
        server.pluginManager.registerEvents(this, this)
        saveDefaultConfig()
        ChessManager.start()
        RequestManager.start()
        addCommand("chess") {
            when (nextArg().lowercase()) {
                "duel" -> {
                    cPlayer(player)
                    cRequire(!ChessManager.isInGame(player), "InGame.You")
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
                            cRequire(!ChessManager.isInGame(opponent), "InGame.Opponent")
                            player.openScreen(ChessGame.SettingsScreen { settings ->
                                duelRequest += Request(player, opponent, ChessGame(settings))
                            })
                        }
                    }
                }
                "stockfish" -> {
                    cPlayer(player)
                    endArgs()
                    cRequire(!ChessManager.isInGame(player), "InGame.You")
                    player.openScreen(ChessGame.SettingsScreen { settings ->
                        ChessGame(settings).addPlayers {
                            human(player, ChessSide.WHITE, false)
                            engine("stockfish", ChessSide.BLACK)
                        }.start()
                    })
                }
                "resign" -> {
                    cPlayer(player)
                    endArgs()
                    val p = cNotNull(ChessManager[player], "NotInGame.You")
                    p.game.stop(ChessGame.EndReason.Resignation(!p.side))
                }
                "leave" -> {
                    cPlayer(player)
                    endArgs()
                    ChessManager.leave(player)
                }
                "draw" -> {
                    cPlayer(player)
                    endArgs()
                    val p = cNotNull(ChessManager[player], "NotInGame.You")
                    val opponent: BukkitChessPlayer = cCast(p.opponent, "NotHuman.Opponent")
                    drawRequest.simpleCall(Request(player, opponent.player, Unit))
                }
                "capture" -> {
                    cPlayer(player)
                    cPerms(player, "greg-chess.debug")
                    val p = cNotNull(ChessManager[player], "NotInGame.You")
                    val pos = if (args.size == 1)
                        p.game.renderer.getPos(Loc.fromLocation(player.location))
                    else
                        cWrongArgument { ChessPosition.parseFromString(nextArg()) }
                    endArgs()
                    p.game.board[pos]?.piece?.capture(p.side)
                    p.game.board.updateMoves()
                    player.sendMessage(ConfigManager.getString("Message.BoardOpDone"))
                }
                "spawn" -> {
                    cPlayer(player)
                    cPerms(player, "greg-chess.debug")
                    cArgs(args, 3, 4)
                    val p = cNotNull(ChessManager[player], "NotInGame.You")
                    val game = p.game
                    cWrongArgument {
                        val square = if (args.size == 3)
                            game.board[Loc.fromLocation(player.location)]!!
                        else
                            game.board[ChessPosition.parseFromString(this[2])]!!
                        val piece = ChessType.valueOf(this[1])
                        square.piece?.capture(p.side)
                        square.piece = ChessPiece(piece, ChessSide.valueOf(this[0]), square)
                        game.board.updateMoves()
                        player.sendMessage(ConfigManager.getString("Message.BoardOpDone"))
                    }
                }
                "move" -> {
                    cPlayer(player)
                    cPerms(player, "greg-chess.debug")
                    cArgs(args, 3, 3)
                    val p = cNotNull(ChessManager[player], "NotInGame.You")
                    val game = p.game
                    cWrongArgument {
                        game.board[ChessPosition.parseFromString(this[2])]?.piece?.capture(p.side)
                        game.board[ChessPosition.parseFromString(this[1])]?.piece
                            ?.move(game.board[ChessPosition.parseFromString(this[2])]!!)
                        game.board.updateMoves()
                        player.sendMessage(ConfigManager.getString("Message.BoardOpDone"))
                    }
                }
                "skip" -> {
                    cPlayer(player)
                    cPerms(player, "greg-chess.debug")
                    endArgs()
                    val game = cNotNull(ChessManager.getGame(player), "NotInGame.You")
                    game.nextTurn()
                    player.sendMessage(ConfigManager.getString("Message.SkippedTurn"))
                }
                "load" -> {
                    cPlayer(player)
                    cPerms(player, "greg-chess.debug")
                    val game = cNotNull(ChessManager.getGame(player), "NotInGame.You")
                    game.board.setFromFEN(
                        FEN.parseFromString(restString())
                    )
                    player.sendMessage(ConfigManager.getString("Message.LoadedFEN"))
                }
                "save" -> {
                    cPlayer(player)
                    endArgs()
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
                        val side = ChessSide.valueOf(nextArg())
                        val time = cNotNull(parseDuration(this[1]), "WrongArgument")
                        when (nextArg().lowercase()) {
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
                    val game = cNotNull(ChessManager.getGame(player), "NotInGame.You")
                    val engines = game.chessPlayers.filterIsInstance<EnginePlayer>()
                    val engine = cNotNull(engines.firstOrNull(), "EngineNotFound")
                    cWrongArgument {
                        when (nextArg().lowercase()) {
                            "set" -> {
                                engine.engine.setOption(nextArg(), restString())
                            }
                            "send" -> engine.engine.sendCommand(restString())
                            else -> cWrongArgument()
                        }
                        player.sendMessage(ConfigManager.getString("Message.EngineCommandSent"))
                    }
                }
                "spectate" -> {
                    cPlayer(player)
                    val toSpectate = cServerPlayer(lastArg())
                    ChessManager.addSpectator(player, toSpectate)
                }
                "reload" -> {
                    cPerms(player, "greg-chess.admin")
                    endArgs()
                    reloadConfig()
                    ChessManager.reload()
                    player.sendMessage(ConfigManager.getString("Message.ConfigReloaded"))
                }
                "dev" -> {
                    cRequire(server.pluginManager.isPluginEnabled("DevHelpPlugin"), "WrongArgument")
                    cPerms(player, "greg-chess.admin")
                    endArgs()
                    server.dispatchCommand(player, "devhelp GregChess ${description.version}")
                }
                "undo" -> {
                    cPlayer(player)
                    endArgs()
                    val p = cNotNull(ChessManager[player], "NotInGame.You")
                    cNotNull(p.game.board.lastMove, "NothingToTakeback")
                    val opponent: BukkitChessPlayer = cCast(p.opponent, "NotHuman.Opponent")
                    takebackRequest.simpleCall(Request(player, opponent.player, Unit))
                }
                "debug" -> {
                    cPerms(player, "greg-chess.admin")
                    cWrongArgument {
                        glog.level = GregLogger.Level.valueOf(lastArg())
                        player.sendMessage(ConfigManager.getString("Message.LevelSet"))
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
                    val p = cNotNull(ChessManager[player], "NotInGame.You")
                    p.isAdmin = !p.isAdmin
                }
                "simul" -> {
                    cPlayer(player)
                    cPerms(player, "greg-chess.admin")
                    cRequire(!ChessManager.isInGame(player), "InGame.You")
                    player.openScreen(ChessGame.SettingsScreen { settings ->
                        val simul = Simul(cNotNull(ChessManager.nextArena(), "NoArenas"), settings)
                        rest().forEach {
                            simul.addGame(player.name, it)
                        }
                        simul.start()
                    })
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
                    "greg-chess.admin", "uci", "reload", "dev", "debug", "admin", "simul"
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

    private fun CommandArgs.selectPiece() =
        when (rest().size) {
            0 -> {
                cPlayer(player)
                val game = cNotNull(ChessManager.getGame(player), "NotInGame.You")
                cNotNull(game.board[Loc.fromLocation(player.location)]?.piece, "PieceNotFound")
            }
            1 -> {
                if (isValidUUID(nextArg())) {
                    cPerms(player, "greg-chess.info")
                    val game = cNotNull(
                        ChessManager.firstGame { UUID.fromString(latestArg()) in it.board },
                        "PieceNotFound"
                    )
                    game.board[UUID.fromString(latestArg())]!!
                } else {
                    cPlayer(player)
                    val game = cNotNull(ChessManager.getGame(player), "NotInGame.You")
                    cNotNull(
                        game.board[ChessPosition.parseFromString(latestArg())]?.piece,
                        "PieceNotFound"
                    )
                }
            }
            else -> throw CommandException("WrongArgumentsNumber")
        }

    private fun CommandArgs.selectGame() =
        when (rest().size) {
            0 -> {
                cPlayer(player)
                cNotNull(ChessManager.getGame(player), "NotInGame.You")
            }
            1 -> {
                cWrongArgument {
                    cPerms(player, "greg-chess.info")
                    cNotNull(ChessManager[UUID.fromString(nextArg())], "GameNotFound")
                }
            }
            else -> throw CommandException("WrongArgumentsNumber")
        }

    override fun onDisable() {
        ChessManager.stop()
    }

    @EventHandler
    fun onTurnEnd(e: ChessGame.TurnEndEvent) {
        if (e.player is BukkitChessPlayer) {
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
            cTry(e.whoClicked, {e.whoClicked.closeInventory()}) {
                if (!holder.finished)
                    if (holder.applyEvent(InventoryPosition.fromIndex(e.slot)))
                        e.whoClicked.closeInventory()
            }
        }
    }

    @EventHandler
    fun onInventoryClose(e: InventoryCloseEvent) {
        val holder = e.inventory.holder
        if (holder is Screen.Holder<*>) {
            cTry(e.player) {
                if (!holder.finished)
                    holder.cancel()
            }
        }
    }
}
