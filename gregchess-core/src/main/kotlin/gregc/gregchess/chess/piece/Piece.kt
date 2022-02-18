package gregc.gregchess.chess.piece

import gregc.gregchess.*
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Chessboard
import gregc.gregchess.registry.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

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

@Serializable(with = PlacedPieceType.Serializer::class)
class PlacedPieceType<T : PlacedPiece>(val serializer: KSerializer<T>) : NameRegistered {
    object Serializer : NameRegisteredSerializer<PlacedPieceType<*>>("PlacedPieceType", Registry.PLACED_PIECE_TYPE)

    override val key get() = Registry.PLACED_PIECE_TYPE[this]

    override fun toString(): String = Registry.PLACED_PIECE_TYPE.simpleElementToString(this)

    @RegisterAll(PlacedPieceType::class)
    companion object {
        internal val AUTO_REGISTER = AutoRegisterType(PlacedPieceType::class) { m, n, _ -> register(m, n) }

        @JvmField
        val BOARD = PlacedPieceType(BoardPiece.serializer())
        @JvmField
        val CAPTURED = PlacedPieceType(CapturedPiece.serializer())

        fun registerCore(module: ChessModule) = AutoRegister(module, listOf(AUTO_REGISTER)).registerAll<PlacedPieceType<*>>()
    }
}

@Serializable(with = PlacedPieceSerializer::class)
interface PlacedPiece {
    val placedPieceType: PlacedPieceType<out @SelfType PlacedPiece>
    val piece: Piece
    val color: Color get() = piece.color
    val type: PieceType get() = piece.type
    val char: Char get() = piece.char

    fun checkExists(board: Chessboard)
    fun checkCanExist(board: Chessboard)

    fun create(board: Chessboard)
    fun destroy(board: Chessboard)
}

internal fun PlacedPiece?.boardPiece() = this as BoardPiece
internal fun PlacedPiece?.capturedPiece() = this as CapturedPiece

fun multiMove(board: Chessboard, vararg moves: Pair<PlacedPiece?, PlacedPiece?>?) {
    val destroyed = mutableListOf<PlacedPiece?>()
    val created = mutableListOf<PlacedPiece?>()
    val realMoves = moves.filterNotNull()
    try {
        for ((o, _) in realMoves)
            o?.checkExists(board)
        for ((o, _) in realMoves) {
            o?.destroy(board)
            destroyed += o
        }
        for ((_, t) in realMoves)
            t?.checkCanExist(board)
        for ((_, t) in realMoves) {
            t?.create(board)
            created += t
        }
        board.callPieceEvent(PieceEvent.Moved(realMoves.toMap()))
    } catch (e: Throwable) {
        for (t in created.asReversed())
            t?.destroy(board)
        for (o in destroyed.asReversed())
            o?.create(board)
        throw e
    }
}

object PlacedPieceSerializer : KeyRegisteredSerializer<PlacedPieceType<*>, PlacedPiece>("PlacedPiece", PlacedPieceType.Serializer) {

    @Suppress("UNCHECKED_CAST")
    override fun PlacedPieceType<*>.valueSerializer(module: SerializersModule): KSerializer<PlacedPiece> =
        serializer as KSerializer<PlacedPiece>

    override val PlacedPiece.key: PlacedPieceType<*> get() = placedPieceType
}

@Serializable
data class CapturedPiece(override val piece: Piece, val capturedBy: Color) : PlacedPiece {
    override val placedPieceType get() = PlacedPieceType.CAPTURED

    override fun checkExists(board: Chessboard) {
        if (board.capturedPieces.none { it == this })
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
    override val placedPieceType get() = PlacedPieceType.BOARD

    override fun checkExists(board: Chessboard) {
        if (board[pos] != this)
            throw PieceDoesNotExistException(this)
    }

    override fun checkCanExist(board: Chessboard) {
        if (board[pos] != null)
            throw PieceAlreadyOccupiesSquareException(board[pos]!!)
    }

    override fun create(board: Chessboard) {
        checkCanExist(board)
        board += this
    }

    override fun destroy(board: Chessboard) {
        checkExists(board)
        board.clearPiece(pos)
    }

    fun sendCreated(board: Chessboard) {
        checkExists(board)
        board.callPieceEvent(PieceEvent.Created(this))
    }

    fun clear(board: Chessboard) {
        checkExists(board)
        board.callPieceEvent(PieceEvent.Cleared(this))
        board.clearPiece(pos)
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
