package gregc.gregchess.chess

import gregc.gregchess.Loc
import gregc.gregchess.chatColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.InventoryHolder

class ChessPlayer(val player: Player, val side: ChessSide, val game: ChessGame, private val silent: Boolean) {
    var held: ChessPiece? = null
    private var heldMoves: List<ChessMove>? = null

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

    fun sendMessage(msg: String) = player.sendMessage(msg)

    private fun sendTitle(title: String, subtitle: String = "") = player.sendTitle(title, subtitle, 10, 70, 20)

    private val pieces
        get() = game.board.piecesOf(side)
    private val king
        get() = pieces.find { it.type == ChessPiece.Type.KING }!!

    private fun getAllowedMoves(piece: ChessPiece): List<ChessMove> =
        game.board.getMoves(piece.pos).filter { game.board.run { it.isLegal } }

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

    fun startTurn() {
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