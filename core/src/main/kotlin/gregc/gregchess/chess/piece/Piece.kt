package gregc.gregchess.chess.piece

import gregc.gregchess.ChessModule
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Chessboard
import gregc.gregchess.registry.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

object PieceRegistryView : DoubleEnumeratedRegistryView<String, Piece> {
    override fun getOrNull(key: RegistryKey<String>): Piece? {
        val (module, name) = key
        return when (name.take(6)) {
            "white_" -> RegistryType.PIECE_TYPE.getOrNull(module, name.drop(6))?.of(Color.WHITE)
            "black_" -> RegistryType.PIECE_TYPE.getOrNull(module, name.drop(6))?.of(Color.BLACK)
            else -> null
        }
    }

    override fun getOrNull(value: Piece): RegistryKey<String>? =
        RegistryType.PIECE_TYPE.getOrNull(value.type)?.let { (module, name) ->
            RegistryKey(module, "${value.color.toString().lowercase()}_$name")
        }

    override val values: Set<Piece>
        get() = RegistryType.PIECE_TYPE.values.flatMap { listOf(white(it), black(it)) }.toSet()

    override fun valuesOf(module: ChessModule): Set<Piece> =
        RegistryType.PIECE_TYPE.valuesOf(module).flatMap { listOf(white(it), black(it)) }.toSet()

    override val keys: Set<RegistryKey<String>>
        get() = values.map { get(it) }.toSet()
}

@Serializable(with = Piece.Serializer::class)
data class Piece(override val type: PieceType, override val color: Color) : NameRegistered, AnyPiece {
    override val key: RegistryKey<String> get() = PieceRegistryView[this]

    object Serializer : NameRegisteredSerializer<Piece>("Piece", PieceRegistryView)

    override val piece: Piece get() = this

    override val char
        get() = when (color) {
            Color.WHITE -> type.char.uppercaseChar()
            Color.BLACK -> type.char
        }
}

fun PieceType.of(color: Color) = Piece(this, color)

fun white(type: PieceType) = type.of(Color.WHITE)
fun black(type: PieceType) = type.of(Color.BLACK)

sealed interface AnyPiece {
    val piece: Piece

    val type get() = piece.type
    val color get() = piece.color
    val char get() = piece.char
}

sealed interface AnyBoardPiece : AnyPiece {
    val pos: Pos
    val hasMoved: Boolean
}

sealed interface AnyCapturedPiece : AnyPiece {
    val capturedBy: Color
}
@Serializable
data class CapturedPiece(override val piece: Piece, override val capturedBy: Color) : AnyCapturedPiece

@Serializable
data class BoardPiece(
    override val pos: Pos,
    override val piece: Piece,
    override val hasMoved: Boolean
) : AnyBoardPiece {

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

    fun capture(by: Color, board: Chessboard): CapturedBoardPiece {
        checkExists(board)
        clear(board)
        val captured = CapturedBoardPiece(this, by)
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

    fun copyInPlace(
        board: Chessboard, pos: Pos = this.pos, piece: Piece = this.piece, hasMoved: Boolean = this.hasMoved
    ): BoardPiece {
        checkExists(board)
        board[this.pos]?.piece = null
        val new = BoardPiece(pos, piece, hasMoved)
        board += new
        return new
    }

    fun getMoves(board: Chessboard) = board.getMoves(pos)
    fun getLegalMoves(board: Chessboard) = board.getLegalMoves(pos)

    fun showMoves(board: Chessboard) {
        board[pos]?.moveMarker = Floor.NOTHING
        board[pos]?.bakedLegalMoves?.forEach { m -> m.show(board) }
        pickUp(board)
    }

    fun hideMoves(board: Chessboard) {
        board[pos]?.moveMarker = null
        board[pos]?.bakedLegalMoves?.forEach { m -> m.hide(board) }
        placeDown(board)
    }

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
data class CapturedBoardPiece(
    val boardPiece: BoardPiece, val captured: CapturedPiece
) : AnyBoardPiece, AnyCapturedPiece {
    constructor(boardPiece: BoardPiece, capturedBy: Color)
            : this(boardPiece, CapturedPiece(boardPiece.piece, capturedBy))

    init {
        require(boardPiece.piece == captured.piece) { "Bad piece types" }
    }

    override val piece: Piece get() = boardPiece.piece
    override val pos: Pos get() = boardPiece.pos
    override val capturedBy: Color get() = captured.capturedBy
    override val hasMoved: Boolean get() = boardPiece.hasMoved

    override fun toString(): String = "CapturedBoardPiece(piece=$piece, pos=$pos, capturedBy=${capturedBy})"

    fun resurrect(board: Chessboard): BoardPiece {
        board[pos]?.piece?.let {
            throw PieceAlreadyOccupiesSquareException(it)
        }
        board -= captured
        board += boardPiece
        board.callEvent(PieceEvent.Resurrected(this))
        return boardPiece
    }

    object Serializer : KSerializer<CapturedBoardPiece> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("CapturedBoardPiece") {
            element<Piece>("piece")
            element<Pos>("pos")
            element<Boolean>("hasMoved")
            element<Color>("capturedBy")
        }

        override fun serialize(encoder: Encoder, value: CapturedBoardPiece) = encoder.encodeStructure(descriptor) {
            encodeSerializableElement(descriptor, 0, Piece.serializer(), value.piece)
            encodeSerializableElement(descriptor, 1, Pos.serializer(), value.pos)
            encodeBooleanElement(descriptor, 2, value.boardPiece.hasMoved)
            encodeSerializableElement(descriptor, 3, encoder.serializersModule.serializer(), value.capturedBy)
        }

        @OptIn(ExperimentalSerializationApi::class)
        override fun deserialize(decoder: Decoder): CapturedBoardPiece = decoder.decodeStructure(descriptor) {
            var piece: Piece? = null
            var pos: Pos? = null
            var hasMoved: Boolean? = null
            var capturedBy: Color? = null
            if (decodeSequentially()) { // sequential decoding protocol
                piece = decodeSerializableElement(descriptor, 0, Piece.serializer())
                pos = decodeSerializableElement(descriptor, 1, Pos.serializer())
                hasMoved = decodeBooleanElement(descriptor, 2)
                capturedBy = decodeSerializableElement(descriptor, 3, decoder.serializersModule.serializer())
            } else {
                while (true) {
                    when (val index = decodeElementIndex(descriptor)) {
                        0 -> piece = decodeSerializableElement(descriptor, index, Piece.serializer())
                        1 -> pos = decodeSerializableElement(descriptor, index, Pos.serializer())
                        2 -> hasMoved = decodeBooleanElement(descriptor, index)
                        3 -> capturedBy =
                            decodeSerializableElement(descriptor, index, decoder.serializersModule.serializer())
                        CompositeDecoder.DECODE_DONE -> break
                        else -> error("Unexpected index: $index")
                    }
                }
            }
            CapturedBoardPiece(BoardPiece(pos!!, piece!!, hasMoved!!), CapturedPiece(piece, capturedBy!!))
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
