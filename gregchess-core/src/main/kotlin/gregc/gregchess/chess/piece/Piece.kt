package gregc.gregchess.chess.piece

import gregc.gregchess.ChessModule
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Chessboard
import gregc.gregchess.registry.*
import kotlinx.serialization.Serializable

object PieceRegistryView : FiniteBiRegistryView<String, Piece> {

    override fun getOrNull(value: Piece): RegistryKey<String>? =
        Registry.PIECE_TYPE.getOrNull(value.type)?.let { (module, name) ->
            RegistryKey(module, "${value.color.toString().lowercase()}_$name")
        }

    override val values: Set<Piece>
        get() = Registry.PIECE_TYPE.values.flatMap { listOf(white(it), black(it)) }.toSet()

    override fun valuesOf(module: ChessModule): Set<Piece> =
        Registry.PIECE_TYPE.valuesOf(module).flatMap { listOf(white(it), black(it)) }.toSet()

    override val keys: Set<RegistryKey<String>>
        get() = values.map { get(it) }.toSet()

    override fun getOrNull(module: ChessModule, key: String): Piece? = when (key.take(6)) {
        "white_" -> Registry.PIECE_TYPE.getOrNull(module, key.drop(6))?.of(Color.WHITE)
        "black_" -> Registry.PIECE_TYPE.getOrNull(module, key.drop(6))?.of(Color.BLACK)
        else -> null
    }

    override fun keysOf(module: ChessModule): Set<String> =
        Registry.PIECE_TYPE.keysOf(module).flatMap { listOf("white_$it", "black_$it") }.toSet()
}

@Serializable(with = Piece.Serializer::class)
data class Piece(val type: PieceType, val color: Color) : NameRegistered {
    override val key: RegistryKey<String> get() = PieceRegistryView[this]

    object Serializer : NameRegisteredSerializer<Piece>("Piece", PieceRegistryView)

    val char : Char
        get() = when (color) {
            Color.WHITE -> type.char.uppercaseChar()
            Color.BLACK -> type.char
        }


}

fun PieceType.of(color: Color) = Piece(this, color)

fun white(type: PieceType) = type.of(Color.WHITE)
fun black(type: PieceType) = type.of(Color.BLACK)

@Serializable(with = PlacedPieceSerializer::class)
interface PlacedPiece {
    val piece: Piece
    val color: Color get() = piece.color
    val type: PieceType get() = piece.type
    val char: Char get() = piece.char

    fun checkExists(board: Chessboard)
    fun checkCanExist(board: Chessboard)

    fun create(board: Chessboard)
    fun destroy(board: Chessboard)
}

fun PlacedPiece?.boardPiece() = this as BoardPiece
fun PlacedPiece?.capturedPiece() = this as CapturedPiece

fun multiMove(game: ChessGame, vararg moves: Pair<PlacedPiece?, PlacedPiece?>?) {
    val realMoves = moves.filterNotNull()
    for ((o,_) in realMoves)
        o?.checkExists(game.board)
    for ((o,_) in realMoves)
        o?.destroy(game.board)
    for ((_,t) in realMoves)
        t?.checkCanExist(game.board)
    for ((_,t) in realMoves)
        t?.create(game.board)
    game.callEvent(PieceEvent.Moved(realMoves.toMap()))
}

object PlacedPieceSerializer : ClassRegisteredSerializer<PlacedPiece>("PlacedPiece", Registry.PLACED_PIECE_CLASS)

@Serializable
data class CapturedPiece(override val piece: Piece, val capturedBy: Color) : PlacedPiece {
    override fun checkExists(board: Chessboard) {
        if (board.data.capturedPieces.none { it == this })
            throw PieceDoesNotExistException(this)
    }

    override fun checkCanExist(board: Chessboard) {}

    override fun create(board: Chessboard) {
        board += this
    }

    override fun destroy(board: Chessboard) {
        checkExists(board)
        board -= this
    }
}

@Serializable
data class BoardPiece(val pos: Pos, override val piece: Piece, val hasMoved: Boolean) : PlacedPiece {

    override fun checkExists(board: Chessboard) {
        if (board[pos] != this)
            throw PieceDoesNotExistException(this)
    }

    override fun checkCanExist(board: Chessboard) {
        if (board[pos] != null)
            throw PieceAlreadyOccupiesSquareException(board[pos]!!)
    }

    override fun create(board: Chessboard) {
        board += this
    }

    override fun destroy(board: Chessboard) {
        checkExists(board)
        board.clearPiece(pos)
    }

    fun sendCreated(game: ChessGame) {
        checkExists(game.board)
        game.callEvent(PieceEvent.Created(this))
    }

    fun clear(game: ChessGame) {
        checkExists(game.board)
        game.callEvent(PieceEvent.Cleared(this))
        game.board.clearPiece(pos)
    }

    fun getMoves(board: Chessboard) = board.getMoves(pos)
    fun getLegalMoves(board: Chessboard) = board.getLegalMoves(pos)

    fun move(target: Pos) = this to this.copy(pos = target, hasMoved = true)
    fun capture(by: Color) = this to CapturedPiece(piece, by)
    fun promote(promotion: Piece) = this to this.copy(piece = promotion)
}

sealed class PieceEvent : ChessEvent {
    class Created(val piece: BoardPiece) : PieceEvent()
    class Cleared(val piece: BoardPiece) : PieceEvent()

    class Moved(val moves: Map<PlacedPiece?, PlacedPiece?>) : PieceEvent()
}

class PieceDoesNotExistException(val piece: PlacedPiece) : Exception(piece.toString())
class PieceAlreadyOccupiesSquareException(val piece: PlacedPiece) : Exception(piece.toString())
