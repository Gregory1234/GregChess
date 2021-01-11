package gregc.gregchess.chess

import gregc.gregchess.Loc
import gregc.gregchess.chatColor
import gregc.gregchess.info
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.InventoryHolder
import java.util.concurrent.*


sealed class ChessPlayer(val side: ChessSide, private val silent: Boolean) {
    class Human(val player: Player, side: ChessSide, silent: Boolean) :
        ChessPlayer(side, silent) {

        override val name = player.name

        override fun sendMessage(msg: String) = player.sendMessage(msg)
        override fun sendTitle(title: String, subtitle: String) = player.sendTitle(title, subtitle, 10, 70, 20)
        fun pickUp(loc: Loc) {
            if (!ChessPosition.fromLoc(loc).isValid()) return
            val piece = game.board[loc] ?: return
            if (piece.side != side) return
            piece.pos.fillFloor(game.world, Material.YELLOW_CONCRETE)
            heldMoves = getAllowedMoves(piece)
            heldMoves?.forEach { it.display(game) }
            held = piece
            game.board.pickUp(piece)
            player.inventory.setItem(0, piece.type.getItem(piece.side))
        }

        fun makeMove(loc: Loc) {
            val newPos = ChessPosition.fromLoc(loc)
            if (!newPos.isValid()) return
            val piece = held ?: return
            val moves = heldMoves ?: return
            if (newPos != piece.pos && newPos !in moves.map { it.target }) return
            piece.pos.clear(game.world)
            moves.forEach { it.target.clear(game.world) }
            held = null
            player.inventory.setItem(0, null)
            if (newPos == piece.pos) {
                game.board.placeDown(piece)
                return
            }
            val chosenMoves = moves.filter { it.target == newPos }
            if (chosenMoves.size != 1) {
                player.openInventory(PawnPromotionScreen(piece, this, chosenMoves.mapNotNull {
                    val p = (it as? ChessMove.Promoting)?.promotion
                    if (p != null)
                        p to it
                    else
                        null
                }).inventory)
            } else {
                finishMove(chosenMoves.first())
            }
        }
    }

    class Engine(override val name: String, side: ChessSide) : ChessPlayer(side, true) {

        private val process: Process = ProcessBuilder("stockfish").start()

        private val executor = Executors.newCachedThreadPool()

        init {
            val reader = process.inputStream.bufferedReader()
            executor.submit(Callable {
                reader.readLine()
            })[5, TimeUnit.SECONDS]
        }

        override fun stop() = process.destroy()

        override fun sendMessage(msg: String) {}

        override fun sendTitle(title: String, subtitle: String) {}

        override fun startTurn() {
            super.startTurn()
            val fen = game.board.getFEN()
            val reader = process.inputStream.bufferedReader()
            process.outputStream.write("isready\n".toByteArray())
            process.outputStream.flush()
            executor.submit(Callable {
                reader.readLine()
            })[5, TimeUnit.SECONDS]
            process.outputStream.write("position fen $fen\n".toByteArray())
            process.outputStream.flush()
            process.outputStream.write("isready\n".toByteArray())
            process.outputStream.flush()
            executor.submit(Callable {
                reader.readLine()
            })[5, TimeUnit.SECONDS]
            process.outputStream.write("go depth 15\n".toByteArray())
            process.outputStream.flush()

            while (true) {
                val line = executor.submit(Callable {
                    reader.readLine().split(" ")
                })[5, TimeUnit.SECONDS]
                info(line)
                if (line[0] == "bestmove") {
                    val origin = ChessPosition.parseFromString(line[1].take(2))
                    val target = ChessPosition.parseFromString(line[1].drop(2).take(2))
                    val promotion = line[1].drop(4).firstOrNull()?.let { ChessPiece.Type.parseFromChar(it) }
                    val move = game.board.getMoves(origin)
                        .first { it.target == target && if (it is ChessMove.Promoting) (it.promotion == promotion) else true }
                    finishMove(move)
                    break
                }
            }
        }
    }

    var held: ChessPiece? = null
    protected var heldMoves: List<ChessMove>? = null

    lateinit var game: ChessGame

    var wantsDraw = false
        set(n) {
            when {
                game[!side].wantsDraw -> game.stop(ChessGame.EndReason.DrawAgreement())
                n -> game[!side].sendTitle(
                    chatColor("&eYour opponent wants a draw."),
                    chatColor("&eType /chess draw to accept")
                )
                else -> game[!side].sendTitle(chatColor("&eYour opponent no longer wants a draw."))
            }
            field = n
        }

    abstract val name: String

    abstract fun sendMessage(msg: String)

    abstract fun sendTitle(title: String, subtitle: String = "")

    private val pieces
        get() = game.board.piecesOf(side)
    private val king
        get() = pieces.find { it.type == ChessPiece.Type.KING }!!

    protected fun getAllowedMoves(piece: ChessPiece): List<ChessMove> =
        game.board.getMoves(piece.pos).filter { game.board.run { it.isLegal } }

    fun finishMove(move: ChessMove) {
        if (game.board[move.origin]?.type == ChessPiece.Type.PAWN || move is ChessMove.Attack)
            game.board.resetMovesSinceLastCapture()
        move.execute(game.board)
        game.board.lastMove = move
        game.nextTurn()
    }

    fun hasTurn(): Boolean = game.currentTurn == side

    class PawnPromotionScreen(
        private val pawn: ChessPiece,
        private val player: ChessPlayer,
        private val moves: List<Pair<ChessPiece.Type, ChessMove>>
    ) : InventoryHolder {
        var finished: Boolean = false
        private val inv = Bukkit.createInventory(this, 9, "Pawn promotion")

        init {
            for ((p, _) in moves) {
                inv.addItem(p.getItem(pawn.side))
            }
        }

        override fun getInventory() = inv

        fun applyEvent(choice: Material?) {
            val m = moves.find { it.first.getMaterial(pawn.side) == choice }?.second ?: moves.first().second
            player.finishMove(m)
            finished = true
        }
    }

    open fun stop() {}

    open fun startTurn() {
        val checkingMoves = game.board.checkingMoves(!side, king.pos)
        if (checkingMoves.isNotEmpty()) {
            var inMate = true
            for (p in pieces) {
                if (getAllowedMoves(p).isNotEmpty()) {
                    inMate = false
                    break
                }
            }
            if (inMate) {
                game.stop(ChessGame.EndReason.Checkmate(!side))
            } else if (!silent) {
                //player.spigot().sendMessage(ChatMessageType.ACTION_BAR, *TextComponent.fromLegacyText(chatColor("&cYou are in check!")))
                sendTitle(chatColor("&eIt is your turn"), chatColor("&cYou are in check!"))
                sendMessage(chatColor("&cYou are in check!"))
            } else {
                sendTitle(chatColor("&cYou are in check!"))
                sendMessage(chatColor("&cYou are in check!"))
            }
        } else {
            var inStalemate = true
            for (p in pieces) {
                if (getAllowedMoves(p).isNotEmpty()) {
                    inStalemate = false
                    break
                }
            }
            if (inStalemate) {
                game.stop(ChessGame.EndReason.Stalemate())
            } else if (!silent) {
                sendTitle(chatColor("&eIt is your turn"))
            }
        }

    }

}