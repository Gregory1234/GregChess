package gregc.gregchess.chess

import gregc.gregchess.chess.component.Chessboard
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class Piece(val type: PieceType, val side: Side) {
    val char
        get() = when (side) {
            Side.WHITE -> type.char.uppercaseChar()
            Side.BLACK -> type.char
        }
}

fun PieceType.of(side: Side) = Piece(this, side)

val Side.pawn get() = PieceType.PAWN.of(this)
val Side.knight get() = PieceType.KNIGHT.of(this)
val Side.bishop get() = PieceType.BISHOP.of(this)
val Side.rook get() = PieceType.ROOK.of(this)
val Side.queen get() = PieceType.QUEEN.of(this)
val Side.king get() = PieceType.KING.of(this)

@Serializable
data class CapturedPos(val by: Side, val row: Int, val pos: Int)

@Serializable
data class CapturedPiece(val piece: Piece, val pos: CapturedPos) {
    val type get() = piece.type
    val side get() = piece.side

    override fun toString() = "CapturedPiece(type=$type, side=$side, pos=$pos)"
}

@Serializable
data class PieceInfo(val pos: Pos, val piece: Piece, val hasMoved: Boolean) {
    val type get() = piece.type
    val side get() = piece.side
    val char get() = piece.char

    fun checkExists(board: Chessboard) {
        require(board[pos]?.piece?.info == this)
    }

    fun pickUp(board: Chessboard) {
        checkExists(board)
        board.callPieceEvent(PieceEvent.Action(this, PieceEvent.ActionType.PICK_UP))
    }

    fun placeDown(board: Chessboard) {
        checkExists(board)
        board.callPieceEvent(PieceEvent.Action(this, PieceEvent.ActionType.PLACE_DOWN))
    }

    fun sendCreated(board: Chessboard) {
        checkExists(board)
        board.callPieceEvent(PieceEvent.Created(this))
    }

    fun clear(board: Chessboard) {
        checkExists(board)
        board.callPieceEvent(PieceEvent.Cleared(this))
        board[pos]?.piece = null
    }

    fun move(target: Pos, board: Chessboard): PieceInfo {
        checkExists(board)
        board[target]?.piece?.let {
            throw PieceAlreadyOccupiesSquareException(it.piece, target)
        }
        val new = copy(pos = target, hasMoved = true)
        board += new
        board[pos]?.piece = null
        board.callPieceEvent(PieceEvent.Moved(new, pos))
        return new
    }
}

sealed class PieceEvent(val piece: PieceInfo) : ChessEvent {
    class Created(piece: PieceInfo) : PieceEvent(piece)
    class Cleared(piece: PieceInfo) : PieceEvent(piece)

    enum class ActionType {
        PICK_UP, PLACE_DOWN
    }

    class Action(piece: PieceInfo, val type: ActionType) : PieceEvent(piece)

    class Moved(piece: PieceInfo, val from: Pos) : PieceEvent(piece)
    class Captured(piece: PieceInfo, val captured: CapturedPiece) : PieceEvent(piece)
    class Promoted(piece: PieceInfo, val promotion: PieceInfo) : PieceEvent(piece)
    class Resurrected(piece: PieceInfo, val captured: CapturedPiece) : PieceEvent(piece)
    class MultiMoved(val moves: Map<PieceInfo, Pos>) : PieceEvent(moves.keys.first())
}

class PieceAlreadyOccupiesSquareException(val piece: Piece, val pos: Pos) : Exception("$pos, $piece")

class BoardPiece(val piece: Piece, initSquare: Square, hasMoved: Boolean = false, val uuid: UUID = UUID.randomUUID()) {
    val type = piece.type
    val side = piece.side

    var square: Square = initSquare
        private set
    val pos
        get() = square.pos
    var hasMoved = hasMoved
        private set

    override fun toString() = "Piece(uniqueId=$uuid, pos=$pos, type=$type, side=$side, hasMoved=$hasMoved)"

    private val board
        get() = square.board

    val info
        get() = PieceInfo(pos, piece, hasMoved)

    fun capture(by: Side): CapturedPiece {
        info.clear(board)
        val captured = CapturedPiece(piece, board.nextCapturedPos(type, by))
        board += captured
        board.callPieceEvent(PieceEvent.Captured(info, captured))
        return captured
    }

    fun promote(promotion: Piece) {
        square.piece = BoardPiece(promotion, square)
        board.callPieceEvent(PieceEvent.Promoted(info, square.piece!!.info))
    }

    fun force(hasMoved: Boolean) {
        this.hasMoved = hasMoved
    }

    fun resurrect(captured: CapturedPiece) {
        board -= captured
        square.piece = this
        board.callPieceEvent(PieceEvent.Resurrected(info, captured))
    }

    companion object {
        fun autoMove(moves: Map<BoardPiece, Square>) {
            val org = moves.map { (p, _) -> p.info to p.pos }.toMap()
            for ((piece, target) in moves) {
                piece.hasMoved = true
                target.piece?.let { p ->
                    if (moves.keys.none { it.square == target })
                        throw PieceAlreadyOccupiesSquareException(p.piece, target.pos)
                }
                piece.square.piece = null
                piece.square = target
            }
            for ((piece, target) in moves) {
                target.piece = piece
                piece.hasMoved = true
            }
            moves.keys.firstOrNull()?.board?.callPieceEvent(PieceEvent.MultiMoved(org))
        }
    }
}