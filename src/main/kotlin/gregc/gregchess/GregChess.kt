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
        validateSender = { it.chess?.hasTurn ?: false }
        onAccept = { (sender, _, _) -> sender.currentGame?.stop(ChessGame.EndReason.DrawAgreement())}
    }

    private val takebackRequest = buildRequestType<Unit> {
        messagesSimple("Takeback", "/chess undo", "/chess undo")
        validateSender = { (it.currentGame?.currentPlayer?.opponent as? HumanChessPlayer)?.player == it }
        onAccept = { (sender, _, _) -> sender.currentGame?.board?.undoLastMove() }
    }

    private val duelRequest = buildRequestType<ChessGame>{
        messagesSimple("Duel", "/chess duel accept", "/chess duel cancel")
        printT = { it.settings.name }
        onAccept = { (sender, receiver, g) ->
            g.addPlayers {
                human(sender, Side.WHITE, sender == receiver)
                human(receiver, Side.BLACK, sender == receiver)
            }.start()
        }
        onCancel = { (_, _, g) ->
            g.arena.game = null
        }
    }

    override fun onEnable() {
        server.pluginManager.registerEvents(this, this)
        saveDefaultConfig()
        ChessManager.start()
        Arena.start()
        RequestManager.start()
        addCommand("chess") {
            when (nextArg().lowercase()) {
                "duel" -> {
                    cPlayer(player)
                    cRequire(!player.human.isInGame(), errorMsg.inGame::you)
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
                            cRequire(!opponent.human.isInGame(), errorMsg.inGame::opponent)
                            player.openScreen(SettingsScreen { settings ->
                                duelRequest += Request(player.human, opponent.human, ChessGame(Arena.cNext(), settings))
                            })
                        }
                    }
                }
                "stockfish" -> {
                    cPlayer(player)
                    endArgs()
                    cRequire(!player.human.isInGame(), errorMsg.inGame::you)
                    player.openScreen(SettingsScreen { settings ->
                        ChessGame(Arena.cNext(), settings).addPlayers {
                            human(player.human, Side.WHITE, false)
                            engine("stockfish", Side.BLACK)
                        }.start()
                    })
                }
                "resign" -> {
                    cPlayer(player)
                    endArgs()
                    val p = cNotNull(player.human.chess, errorMsg.notInGame::you)
                    p.game.stop(ChessGame.EndReason.Resignation(!p.side))
                }
                "leave" -> {
                    cPlayer(player)
                    endArgs()
                    ChessManager.leave(player.human)
                }
                "draw" -> {
                    cPlayer(player)
                    endArgs()
                    val p = cNotNull(player.human.chess, errorMsg.notInGame::you)
                    val opponent: HumanChessPlayer = cCast(p.opponent, errorMsg.notHuman::opponent)
                    drawRequest.simpleCall(Request(player.human, opponent.player, Unit))
                }
                "capture" -> {
                    cPlayer(player)
                    cPerms(player, "greg-chess.debug")
                    val p = cNotNull(player.human.chess, errorMsg.notInGame::you)
                    val pos = if (args.size == 1)
                        cNotNull(p.game.withRenderer<Loc, Pos> { it.getPos(Loc.fromLocation(player.location)) }, errorMsg::rendererNotFound)
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
                    val p = cNotNull(player.human.chess, errorMsg.notInGame::you)
                    val game = p.game
                    cWrongArgument {
                        val square = if (args.size == 3)
                            game.board[Loc.fromLocation(player.location)]!!
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
                    val p = cNotNull(player.human.chess, errorMsg.notInGame::you)
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
                    val game = cNotNull(player.human.currentGame, errorMsg.notInGame::you)
                    game.nextTurn()
                    player.sendMessage(Config.message.skippedTurn)
                }
                "load" -> {
                    cPlayer(player)
                    cPerms(player, "greg-chess.debug")
                    val game = cNotNull(player.human.currentGame, errorMsg.notInGame::you)
                    game.board.setFromFEN(
                        FEN.parseFromString(restString())
                    )
                    player.sendMessage(Config.message.loadedFEN)
                }
                "save" -> {
                    cPlayer(player)
                    endArgs()
                    val game = cNotNull(player.human.currentGame, errorMsg.notInGame::you)
                    val message = TextComponent(Config.message.copyFEN)
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
                    val game = cNotNull(player.human.currentGame, errorMsg.notInGame::you)
                    val clock = cNotNull(game.clock, errorMsg::clockNotFound)
                    cWrongArgument {
                        val side = Side.valueOf(nextArg())
                        val time = cNotNull(parseDuration(this[1]), errorMsg::wrongArgument)
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
                    val game = cNotNull(player.human.currentGame, errorMsg.notInGame::you)
                    val engines = game.players.toList().filterIsInstance<EnginePlayer>()
                    val engine = cNotNull(engines.firstOrNull(), errorMsg::engineNotFound)
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
                    player.human.spectatedGame = cNotNull(toSpectate.human.currentGame, errorMsg.notInGame::player)
                }
                "reload" -> {
                    cPerms(player, "greg-chess.admin")
                    endArgs()
                    reloadConfig()
                    Arena.reload()
                    player.sendMessage(Config.message.configReloaded)
                }
                "dev" -> {
                    cRequire(server.pluginManager.isPluginEnabled("DevHelpPlugin"), errorMsg::wrongArgument)
                    cPerms(player, "greg-chess.admin")
                    endArgs()
                    server.dispatchCommand(player, "devhelp GregChess ${description.version}")
                }
                "undo" -> {
                    cPlayer(player)
                    endArgs()
                    val p = cNotNull(player.human.chess, errorMsg.notInGame::you)
                    cNotNull(p.game.board.lastMove, errorMsg::nothingToTakeback)
                    val opponent: HumanChessPlayer = cCast(p.opponent, errorMsg.notHuman::opponent)
                    takebackRequest.simpleCall(Request(player.human, opponent.player, Unit))
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
                val game = cNotNull(player.human.currentGame, errorMsg.notInGame::you)
                cNotNull(game.board[Loc.fromLocation(player.location)]?.piece, errorMsg::pieceNotFound)
            }
            1 -> {
                if (isValidUUID(nextArg())) {
                    cPerms(player, "greg-chess.info")
                    val game = cNotNull(
                        ChessManager.firstGame { UUID.fromString(latestArg()) in it.board },
                        errorMsg::pieceNotFound
                    )
                    game.board[UUID.fromString(latestArg())]!!
                } else {
                    cPlayer(player)
                    val game = cNotNull(player.human.currentGame, errorMsg.notInGame::you)
                    cNotNull(game.board[Pos.parseFromString(latestArg())]?.piece, errorMsg::pieceNotFound)
                }
            }
            else -> throw CommandException(errorMsg::wrongArgumentsNumber)
        }

    private fun CommandArgs.selectGame() =
        when (rest().size) {
            0 -> {
                cPlayer(player)
                cNotNull(player.human.currentGame, errorMsg.notInGame::you)
            }
            1 -> {
                cWrongArgument {
                    cPerms(player, "greg-chess.info")
                    cNotNull(ChessManager[UUID.fromString(nextArg())], errorMsg::gameNotFound)
                }
            }
            else -> throw CommandException(errorMsg::wrongArgumentsNumber)
        }

    override fun onDisable() {
        ChessManager.stop()
    }

    @EventHandler
    fun onTurnEnd(e: ChessGame.TurnEndEvent) {
        if (e.player is HumanChessPlayer) {
            drawRequest.quietRemove(e.player.player)
            takebackRequest.quietRemove(e.player.player)
        }
    }

    @EventHandler
    fun onGameEnd(e: ChessGame.EndEvent) {
        e.game.players.forEachReal {
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
