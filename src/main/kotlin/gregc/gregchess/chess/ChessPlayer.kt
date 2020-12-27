package gregc.gregchess.chess

import gregc.gregchess.Loc
import gregc.gregchess.chatColor
import org.bukkit.Material
import org.bukkit.entity.Player

class ChessPlayer(val player: Player, val side: ChessSide, private val game: ChessGame, private val silent: Boolean) {
    var held: ChessPiece? = null
    private var heldMoves: List<ChessMoveScheme.Move>? = null
    var lastMove: ChessMoveScheme.Move? = null

    var wantsDraw = false
        set(n) {
            when {
                game[!side].wantsDraw -> game.stop(ChessGame.EndReason.DrawAgreement())
                n -> game[!side].sendTitle(chatColor("&eYour opponent wants a draw."), chatColor("&eType /chess draw to accept"))
                else -> game[!side].sendTitle(chatColor("&eYour opponent no longer wants a draw."))
            }
            field = n
        }

    fun sendMessage(msg: String) = player.sendMessage(msg)

    private fun sendTitle(title: String, subtitle: String = "") = player.sendTitle(title, subtitle, 10, 70, 20)

    val pieces
        get() = game.board[side]
    private val king
        get() = pieces.find { it.type == ChessPiece.Type.KING }!!

    private var checkingMoves = emptyList<ChessMoveScheme.Move>()
    private var pinnedPieces = emptyMap<ChessPiece, ChessMoveScheme.Move>()

    private fun getAllowedMoves(piece: ChessPiece) = piece.getMoves().let { moves ->
        when (piece.type) {
            ChessPiece.Type.KING ->
                moves.filter { it.target !in game.board.getAttackedPositions(!side, listOf(piece.pos)) }
            else -> moves.filter { m -> checkingMoves.all { m.target in it.blocks + it.origin } }.filter { m ->
                val pin = pinnedPieces[piece]
                if (pin == null)
                    true
                else
                    m.target in (pin.blocks + pin.cont + pin.origin)
            }
        }
    }

    fun pickUp(loc: Loc) {
        if (!ChessPosition.fromLoc(loc).isValid()) return
        val piece = game.board[loc] ?: return
        if (piece.side != side) return
        piece.pos.fillFloor(game.arena.world, Material.YELLOW_CONCRETE)
        heldMoves = getAllowedMoves(piece)
        heldMoves?.forEach { it.display() }
        held = piece
        piece.pickUp()
        player.inventory.setItem(0, piece.type.getItem(piece.side))
    }

    fun makeMove(loc: Loc) {
        val newPos = ChessPosition.fromLoc(loc)
        if (!newPos.isValid()) return
        val piece = held ?: return
        val moves = heldMoves ?: return
        if (newPos != piece.pos && newPos !in moves.map { it.target }) return
        game.board.clearMarkings()
        held = null
        player.inventory.setItem(0, null)
        if (newPos == piece.pos) {
            piece.placeBackDown()
            return
        }
        val move = (moves.find { it.target == newPos } ?: return)
        if (piece.type == ChessPiece.Type.PAWN || null in move.elements.map { it.target })
            game.resetMovesSinceLastCapture()
        move.execute()
        lastMove = move
    }

    fun hasTurn(): Boolean = game.currentTurn == side

    fun promote(pawn: ChessPiece) {
        player.openInventory(ChessGame.PawnPromotionScreen(pawn, game).inventory)
    }

    fun startTurn() {
        checkingMoves = game.board.allCheckMoves(king.pos, !side)
        pinnedPieces = game.board.allPinnedPieces(king.pos, !side)
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
                if (p.getMoves().isNotEmpty()) {
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