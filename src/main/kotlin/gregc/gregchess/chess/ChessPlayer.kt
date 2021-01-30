package gregc.gregchess.chess

import gregc.gregchess.Loc
import gregc.gregchess.chatColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.InventoryHolder


sealed class ChessPlayer(val side: ChessSide, private val silent: Boolean) {
    class Human(val player: Player, side: ChessSide, silent: Boolean) :
        ChessPlayer(side, silent) {

        override val name = player.name

        override fun sendMessage(msg: String) = player.sendMessage(msg)
        override fun sendTitle(title: String, subtitle: String) = player.sendTitle(title, subtitle, 10, 70, 20)
        fun pickUp(loc: Loc) {
            if (!game.board.renderer.getPos(loc).isValid()) return
            val piece = game.board[loc] ?: return
            if (piece.side != side) return
            piece.square.moveMarker = Material.YELLOW_CONCRETE
            piece.square.render()
            heldMoves = getAllowedMoves(piece)
            heldMoves?.forEach { it.render() }
            held = piece
            piece.pickUp()
            player.inventory.setItem(0, piece.type.getItem(piece.side))
        }

        fun makeMove(loc: Loc) {
            val newSquare = game.board.getSquare(loc) ?: return
            val piece = held ?: return
            val moves = heldMoves ?: return
            if (newSquare != piece.square && newSquare !in moves.map { it.target }) return
            piece.square.moveMarker = null
            piece.square.render()
            moves.forEach { it.clear() }
            held = null
            player.inventory.setItem(0, null)
            if (newSquare == piece.square) {
                piece.placeDown()
                return
            }
            val chosenMoves = moves.filter { it.target == newSquare }
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

    class Engine(val engine: ChessEngine, side: ChessSide) : ChessPlayer(side, true) {

        override val name = engine.name

        override fun stop() = engine.stop()

        override fun sendMessage(msg: String) {}

        override fun sendTitle(title: String, subtitle: String) {}

        override fun startTurn() {
            super.startTurn()
            val str = engine.getMove(game.board.getFEN())
            val origin = ChessPosition.parseFromString(str.take(2))
            val target = ChessPosition.parseFromString(str.drop(2).take(2))
            val promotion = str.drop(4).firstOrNull()?.let { ChessType.parseFromChar(it) }
            val move = game.board.getMoves(origin)
                .first { it.target.pos == target && if (it is ChessMove.Promoting) (it.promotion == promotion) else true }
            finishMove(move)
        }
    }

    var held: ChessPiece? = null
    protected var heldMoves: List<ChessMove>? = null

    lateinit var game: ChessGame

    abstract val name: String

    abstract fun sendMessage(msg: String)

    abstract fun sendTitle(title: String, subtitle: String = "")

    private val pieces
        get() = game.board.piecesOf(side)
    private val king
        get() = pieces.find { it.type == ChessType.KING }!!

    protected fun getAllowedMoves(piece: ChessPiece): List<ChessMove> =
        game.board.getMoves(piece.pos).filter { game.board.run { it.isLegal } }

    fun finishMove(move: ChessMove) {
        val data = move.execute()
        game.board.lastMove?.clear()
        game.board.lastMove = data
        game.board.lastMove?.render()
        game.nextTurn()
    }

    fun hasTurn(): Boolean = game.currentTurn == side

    class PawnPromotionScreen(
        private val pawn: ChessPiece,
        private val player: ChessPlayer,
        private val moves: List<Pair<ChessType, ChessMove>>
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
        val checkingMoves = game.board.checkingMoves(!side, king.square)
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