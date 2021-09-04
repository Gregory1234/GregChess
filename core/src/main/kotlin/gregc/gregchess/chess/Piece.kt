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
        val new = copyInPlace(board, pos = target, hasMoved = true)
        board.callPieceEvent(PieceEvent.Moved(new, pos))
        return new
    }

    fun capture(by: Side, board: Chessboard): CapturedBoardPiece {
        checkExists(board)
        clear(board)
        val captured = CapturedBoardPiece(this, board.nextCapturedPos(type, by))
        board += captured.captured
        board.callPieceEvent(PieceEvent.Captured(captured))
        return captured
    }

    fun promote(promotion: Piece, board: Chessboard): PieceInfo {
        checkExists(board)
        val new = copyInPlace(board, piece = promotion, hasMoved = false)
        board.callPieceEvent(PieceEvent.Promoted(this, new))
        return new
    }

    fun copyInPlace(board: Chessboard, pos: Pos = this.pos, piece: Piece = this.piece, hasMoved: Boolean = this.hasMoved): PieceInfo {
        checkExists(board)
        board[this.pos]?.piece = null
        val new = PieceInfo(pos, piece, hasMoved)
        board += new
        return new
    }

    companion object {
        fun autoMove(moves: Map<PieceInfo, Pos>, board: Chessboard): Map<PieceInfo, PieceInfo> {
            val pieces = moves.keys
            for (piece in pieces) {
                piece.checkExists(board)
                board[piece.pos]?.piece = null
            }
            for ((piece, target) in moves)
                if (board[target]?.piece != null && target !in pieces.map { it.pos })
                    throw PieceAlreadyOccupiesSquareException(piece.piece, target)
            val new = moves.mapValues { (piece, target) ->
                piece.copy(pos = target, hasMoved = true).also { board += it }
            }
            board.callPieceEvent(PieceEvent.MultiMoved(new))
            return new
        }
    }
}

// TODO: make component2 CapturedPiece
@Serializable
data class CapturedBoardPiece(val piece: PieceInfo, val capturedPos: CapturedPos) {
    val captured: CapturedPiece = CapturedPiece(piece.piece, capturedPos)
    val type: PieceType get() = piece.type
    val side: Side get() = piece.side
    val pos: Pos get() = piece.pos
    val hasMoved: Boolean get() = piece.hasMoved

    fun resurrect(board: Chessboard): PieceInfo {
        board[pos]?.piece?.let {
            throw PieceAlreadyOccupiesSquareException(it.piece, pos)
        }
        board -= captured
        board += piece
        board.callPieceEvent(PieceEvent.Resurrected(this))
        return piece
    }
}

sealed class PieceEvent : ChessEvent {
    class Created(val piece: PieceInfo) : PieceEvent()
    class Cleared(val piece: PieceInfo) : PieceEvent()

    enum class ActionType {
        PICK_UP, PLACE_DOWN
    }

    class Action(val piece: PieceInfo, val type: ActionType) : PieceEvent()

    class Moved(val piece: PieceInfo, val from: Pos) : PieceEvent()
    class Captured(val piece: CapturedBoardPiece) : PieceEvent()
    class Promoted(val piece: PieceInfo, val promotion: PieceInfo) : PieceEvent()
    class Resurrected(val piece: CapturedBoardPiece) : PieceEvent()
    class MultiMoved(val moves: Map<PieceInfo, PieceInfo>) : PieceEvent()
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
}