package gregc.gregchess.bukkit

import gregc.gregchess.*
import gregc.gregchess.bukkit.chess.*
import gregc.gregchess.bukkit.chess.component.GameEndEvent
import gregc.gregchess.bukkit.chess.component.TurnEndEvent
import gregc.gregchess.chess.*
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

object GregChess : Listener {
    class Plugin : JavaPlugin() {
        companion object {
            lateinit var INSTANCE: Plugin
                private set
        }

        init {
            INSTANCE = this
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

    private val CLOCK_NOT_FOUND = err("ClockNotFound")
    private val ENGINE_NOT_FOUND = err("EngineNotFound")
    private val STOCKFISH_NOT_FOUND = err("StockfishNotFound")
    private val PIECE_NOT_FOUND = err("PieceNotFound")
    private val GAME_NOT_FOUND = err("GameNotFound")
    private val NOTHING_TO_TAKEBACK = err("NothingToTakeback")

    private val BOARD_OP_DONE = message("BoardOpDone")
    private val SKIPPED_TURN = message("SkippedTurn")
    private val TIME_OP_DONE = message("TimeOpDone")
    private val ENGINE_COMMAND_SENT = message("EngineCommandSent")
    private val LOADED_FEN = message("LoadedFEN")
    private val CONFIG_RELOADED = message("ConfigReloaded")

    private val drawRequest = RequestManager.register("Draw", "/chess draw", "/chess draw")

    private val takebackRequest = RequestManager.register("Takeback", "/chess undo", "/chess undo")

    private val duelRequest = RequestManager.register("Duel", "/chess duel accept", "/chess duel cancel")

    private fun CommandArgs.perms(c: String = latestArg().lowercase()) = cPerms(player, "greg-chess.chess.$c")

    fun onEnable() {
        registerEvents()
        plugin.saveDefaultConfig()
        GregChessModule.extensions += BukkitGregChessModule
        GregChessModule.load()
        ChessGameManager.start()
        ArenaManager.start()
        RequestManager.start()

        plugin.addCommand("chess") {
            when (nextArg().lowercase()) {
                "duel" -> {
                    cPlayer(player)
                    perms()
                    cRequire(!player.human.isInGame, YOU_IN_GAME)
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
                            cRequire(!opponent.human.isInGame, OPPONENT_IN_GAME)
                            interact {
                                val settings = player.openSettingsMenu()
                                if (settings != null) {
                                    val res = duelRequest.call(RequestData(player, opponent, settings.name))
                                    if (res == RequestResponse.ACCEPT) {
                                        ChessGame(settings).addPlayers {
                                            human(player.human, white, player == opponent)
                                            human(opponent.human, black, player == opponent)
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
                    cRequire(Stockfish.Config.hasStockfish, STOCKFISH_NOT_FOUND)
                    endArgs()
                    cRequire(!player.human.isInGame, YOU_IN_GAME)
                    interact {
                        val settings = player.openSettingsMenu()
                        if (settings != null)
                            ChessGame(settings).addPlayers {
                                human(player.human, white, false)
                                engine(Stockfish(), black)
                            }.start()
                    }
                }
                "resign" -> {
                    cPlayer(player)
                    perms()
                    endArgs()
                    val p = player.human.chess.cNotNull(YOU_NOT_IN_GAME)
                    p.game.stop(p.side.lostBy(EndReason.RESIGNATION))
                }
                "leave" -> {
                    cPlayer(player)
                    perms()
                    endArgs()
                    ChessGameManager.leave(player.human)
                }
                "draw" -> {
                    cPlayer(player)
                    perms()
                    endArgs()
                    val p = player.human.chess.cNotNull(YOU_NOT_IN_GAME)
                    val opponent: HumanChessPlayer = p.opponent.cCast(OPPONENT_NOT_HUMAN)
                    interact {
                        drawRequest.invalidSender(player) { !p.hasTurn }
                        val res = drawRequest.call(RequestData(player, opponent.player.bukkit, ""), true)
                        if (res == RequestResponse.ACCEPT) {
                            p.game.stop(drawBy(EndReason.DRAW_AGREEMENT))
                        }
                    }
                }
                "capture" -> {
                    cPlayer(player)
                    perms()
                    val p = player.human.chess.cNotNull(YOU_NOT_IN_GAME)
                    val pos = if (args.size == 1)
                        p.game.renderer.getPos(player.location.toLoc())
                    else
                        cWrongArgument { Pos.parseFromString(nextArg()) }
                    endArgs()
                    p.game.board[pos]?.piece?.capture(p.side)
                    p.game.board.updateMoves()
                    player.human.sendMessage(BOARD_OP_DONE)
                }
                "spawn" -> {
                    cPlayer(player)
                    perms()
                    cArgs(args, 3, 4)
                    val p = player.human.chess.cNotNull(YOU_NOT_IN_GAME)
                    val game = p.game
                    cWrongArgument {
                        val square = if (args.size == 3)
                            game.board[game.renderer.getPos(player.location.toLoc())]!!
                        else
                            game.board[Pos.parseFromString(this[2])]!!
                        val key = this[1].toKey()
                        val piece = GregChessModule.getModule(key.namespace).pieceTypes.first { it.name.lowercase() == key.key }
                        square.piece?.capture(p.side)
                        square.piece = BoardPiece(piece.of(Side.valueOf(this[0])), square)
                        game.board.updateMoves()
                        player.human.sendMessage(BOARD_OP_DONE)
                    }
                }
                "move" -> {
                    cPlayer(player)
                    perms()
                    cArgs(args, 3, 3)
                    val p = player.human.chess.cNotNull(YOU_NOT_IN_GAME)
                    val game = p.game
                    cWrongArgument {
                        game.board[Pos.parseFromString(this[2])]?.piece?.capture(p.side)
                        game.board[Pos.parseFromString(this[1])]?.piece?.move(game.board[Pos.parseFromString(this[2])]!!)
                        game.board.updateMoves()
                        player.human.sendMessage(BOARD_OP_DONE)
                    }
                }
                "skip" -> {
                    cPlayer(player)
                    perms()
                    endArgs()
                    val game = player.human.currentGame.cNotNull(YOU_NOT_IN_GAME)
                    game.nextTurn()
                    player.human.sendMessage(SKIPPED_TURN)
                }
                "load" -> {
                    cPlayer(player)
                    perms()
                    val game = player.human.currentGame.cNotNull(YOU_NOT_IN_GAME)
                    game.board.setFromFEN(FEN.parseFromString(restString()))
                    player.human.sendMessage(LOADED_FEN)
                }
                "save" -> {
                    cPlayer(player)
                    perms()
                    endArgs()
                    val game = player.human.currentGame.cNotNull(YOU_NOT_IN_GAME)
                    player.human.sendFEN(game.board.getFEN())
                }
                "time" -> {
                    cPlayer(player)
                    perms()
                    cArgs(args, 4, 4)
                    val game = player.human.currentGame.cNotNull(YOU_NOT_IN_GAME)
                    val clock = game.clock.cNotNull(CLOCK_NOT_FOUND)
                    cWrongArgument {
                        val side = Side.valueOf(nextArg())
                        val time = this[1].asDurationOrNull().cNotNull(WRONG_ARGUMENT)
                        when (nextArg().lowercase()) {
                            "add" -> clock.addTime(side, time)
                            "set" -> clock.setTime(side, time)
                            else -> cWrongArgument()
                        }
                        player.human.sendMessage(TIME_OP_DONE)
                    }
                }
                "uci" -> {
                    cPlayer(player)
                    perms()
                    val game = player.human.currentGame.cNotNull(YOU_NOT_IN_GAME)
                    val engines = game.players.toList().filterIsInstance<EnginePlayer>()
                    val engine = engines.firstOrNull().cNotNull(ENGINE_NOT_FOUND)
                    cWrongArgument {
                        when (nextArg().lowercase()) {
                            "set" -> {
                                engine.engine.setOption(nextArg(), restString())
                            }
                            "send" -> engine.engine.sendCommand(restString())
                            else -> cWrongArgument()
                        }
                        player.human.sendMessage(ENGINE_COMMAND_SENT)
                    }
                }
                "spectate" -> {
                    cPlayer(player)
                    perms()
                    val toSpectate = cServerPlayer(lastArg())
                    player.human.spectatedGame = toSpectate.human.currentGame.cNotNull(PLAYER_NOT_IN_GAME)
                }
                "reload" -> {
                    perms()
                    endArgs()
                    plugin.reloadConfig()
                    ArenaManager.reload()
                    player.sendMessage(CONFIG_RELOADED.get())
                }
                "dev" -> {
                    cRequire(Bukkit.getPluginManager().isPluginEnabled("DevHelpPlugin"), WRONG_ARGUMENTS_NUMBER)
                    perms()
                    endArgs()
                    Bukkit.dispatchCommand(player, "devhelp GregChess ${plugin.description.version}")
                }
                "undo" -> {
                    cPlayer(player)
                    perms()
                    endArgs()
                    val p = player.human.chess.cNotNull(YOU_NOT_IN_GAME)
                    p.game.board.lastMove.cNotNull(NOTHING_TO_TAKEBACK)
                    val opponent: HumanChessPlayer = p.opponent.cCast(OPPONENT_NOT_HUMAN)
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
                    "spawn" -> ifPermission("spawn", GregChessModule.modules.flatMap { p -> p.pieceTypes.map { p.namespace + ":" + it.name.lowercase() } }.toTypedArray())
                    "time" -> ifPermission("time", arrayOf("add", "set"))
                    else -> listOf()
                }
                else -> listOf()
            }?.filter { it.startsWith(args.last()) || it.startsWith("gregchess:" + args.last()) }
        }
    }

    private fun CommandArgs.selectPiece() =
        when (rest().size) {
            0 -> {
                cPlayer(player)
                perms("info.ingame")
                val game = player.human.currentGame.cNotNull(YOU_NOT_IN_GAME)
                game.board[game.renderer.getPos(player.location.toLoc())]?.piece.cNotNull(PIECE_NOT_FOUND)
            }
            1 -> {
                if (isValidUUID(nextArg())) {
                    perms("info.remote")
                    val game = ChessGameManager.firstGame { UUID.fromString(latestArg()) in it.board }
                        .cNotNull(PIECE_NOT_FOUND)
                    game.board[UUID.fromString(latestArg())]!!
                } else {
                    cPlayer(player)
                    perms("info.ingame")
                    val game = player.human.currentGame.cNotNull(YOU_NOT_IN_GAME)
                    game.board[Pos.parseFromString(latestArg())]?.piece.cNotNull(PIECE_NOT_FOUND)
                }
            }
            else -> throw CommandException(WRONG_ARGUMENTS_NUMBER)
        }

    private fun CommandArgs.selectGame() =
        when (rest().size) {
            0 -> {
                cPlayer(player)
                perms("info.ingame")
                player.human.currentGame.cNotNull(YOU_NOT_IN_GAME)
            }
            1 -> {
                cWrongArgument {
                    perms("info.remote")
                    ChessGameManager[UUID.fromString(nextArg())].cNotNull(GAME_NOT_FOUND)
                }
            }
            else -> throw CommandException(WRONG_ARGUMENTS_NUMBER)
        }

    fun onDisable() {
        ChessGameManager.stop()
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
