package gregc.gregchess.chess

import gregc.gregchess.chess.component.Chessboard
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.*

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
data class BoardPiece(val pos: Pos, val piece: Piece, val hasMoved: Boolean) {
    val type get() = piece.type
    val side get() = piece.side
    val char get() = piece.char

    fun checkExists(board: Chessboard) {
        if (board[pos]?.piece != this)
            throw PieceDoesNotExistException(this)
    }

    fun pickUp(board: Chessboard) {
        checkExists(board)
        board.callEvent(PieceEvent.Action(this, PieceEvent.ActionType.PICK_UP))
    }

    fun placeDown(board: Chessboard) {
        checkExists(board)
        board.callEvent(PieceEvent.Action(this, PieceEvent.ActionType.PLACE_DOWN))
    }

    fun sendCreated(board: Chessboard) {
        checkExists(board)
        board.callEvent(PieceEvent.Created(this))
    }

    fun clear(board: Chessboard) {
        checkExists(board)
        board.callEvent(PieceEvent.Cleared(this))
        board[pos]?.piece = null
    }

    fun move(target: Pos, board: Chessboard): BoardPiece {
        checkExists(board)
        board[target]?.piece?.let {
            throw PieceAlreadyOccupiesSquareException(it)
        }
        val new = copyInPlace(board, pos = target, hasMoved = true)
        board.callEvent(PieceEvent.Moved(new, pos))
        return new
    }

    fun capture(by: Side, board: Chessboard): CapturedBoardPiece {
        checkExists(board)
        clear(board)
        val captured = CapturedBoardPiece(this, board.nextCapturedPos(type, by))
        board += captured.captured
        board.callEvent(PieceEvent.Captured(captured))
        return captured
    }

    fun promote(promotion: Piece, board: Chessboard): BoardPiece {
        checkExists(board)
        val new = copyInPlace(board, piece = promotion, hasMoved = false)
        board.callEvent(PieceEvent.Promoted(this, new))
        return new
    }

    fun copyInPlace(board: Chessboard, pos: Pos = this.pos, piece: Piece = this.piece, hasMoved: Boolean = this.hasMoved): BoardPiece {
        checkExists(board)
        board[this.pos]?.piece = null
        val new = BoardPiece(pos, piece, hasMoved)
        board += new
        return new
    }

    fun getMoves(board: Chessboard) = board.getMoves(pos)
    fun getLegalMoves(board: Chessboard) = board.getLegalMoves(pos)

    companion object {
        fun autoMove(moves: Map<BoardPiece, Pos>, board: Chessboard): Map<BoardPiece, BoardPiece> {
            val pieces = moves.keys
            for (piece in pieces) {
                piece.checkExists(board)
                board[piece.pos]?.piece = null
            }
            for ((piece, target) in moves)
                if (board[target]?.piece != null && target !in pieces.map { it.pos })
                    throw PieceAlreadyOccupiesSquareException(piece)
            val new = moves.mapValues { (piece, target) ->
                piece.copy(pos = target, hasMoved = true).also { board += it }
            }
            board.callEvent(PieceEvent.MultiMoved(new))
            return new
        }
    }
}

@Serializable(with = CapturedBoardPiece.Serializer::class)
data class CapturedBoardPiece(val piece: BoardPiece, val captured: CapturedPiece) {
    constructor(piece: BoardPiece, capturedPos: CapturedPos): this(piece, CapturedPiece(piece.piece, capturedPos))
    init {
        require(piece.piece == captured.piece)
    }

    val type: PieceType get() = piece.type
    val side: Side get() = piece.side
    val pos: Pos get() = piece.pos

    fun resurrect(board: Chessboard): BoardPiece {
        board[pos]?.piece?.let {
            throw PieceAlreadyOccupiesSquareException(it)
        }
        board -= captured
        board += piece
        board.callEvent(PieceEvent.Resurrected(this))
        return piece
    }

    object Serializer : KSerializer<CapturedBoardPiece> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("CapturedBoardPiece") {
            element("piece", Piece.serializer().descriptor)
            element("pos", Pos.serializer().descriptor)
            element("hasMoved", Boolean.serializer().descriptor)
            element("capturedPos", CapturedPos.serializer().descriptor)
        }

        override fun serialize(encoder: Encoder, value: CapturedBoardPiece) = encoder.encodeStructure(descriptor) {
            encodeSerializableElement(descriptor, 0, Piece.serializer(), value.piece.piece)
            encodeSerializableElement(descriptor, 1, Pos.serializer(), value.pos)
            encodeBooleanElement(descriptor, 2, value.piece.hasMoved)
            encodeSerializableElement(descriptor, 3, CapturedPos.serializer(), value.captured.pos)
        }

        override fun deserialize(decoder: Decoder): CapturedBoardPiece = decoder.decodeStructure(descriptor) {
            var piece: Piece? = null
            var pos: Pos? = null
            var hasMoved: Boolean? = null
            var capturedPos: CapturedPos? = null
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> piece = decodeSerializableElement(descriptor, index, Piece.serializer())
                    1 -> pos = decodeSerializableElement(descriptor, index, Pos.serializer())
                    2 -> hasMoved = decodeBooleanElement(descriptor, index)
                    3 -> capturedPos = decodeSerializableElement(descriptor, index, CapturedPos.serializer())
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
            CapturedBoardPiece(BoardPiece(pos!!, piece!!, hasMoved!!), CapturedPiece(piece, capturedPos!!))
        }
    }
}

sealed class PieceEvent : ChessEvent {
    class Created(val piece: BoardPiece) : PieceEvent()
    class Cleared(val piece: BoardPiece) : PieceEvent()

    enum class ActionType {
        PICK_UP, PLACE_DOWN
    }

    class Action(val piece: BoardPiece, val type: ActionType) : PieceEvent()

    class Moved(val piece: BoardPiece, val from: Pos) : PieceEvent()
    class Captured(val piece: CapturedBoardPiece) : PieceEvent()
    class Promoted(val piece: BoardPiece, val promotion: BoardPiece) : PieceEvent()
    class Resurrected(val piece: CapturedBoardPiece) : PieceEvent()
    class MultiMoved(val moves: Map<BoardPiece, BoardPiece>) : PieceEvent()
}

class PieceDoesNotExistException(val piece: BoardPiece) : Exception(piece.toString())
class PieceAlreadyOccupiesSquareException(val piece: BoardPiece) : Exception(piece.toString())
