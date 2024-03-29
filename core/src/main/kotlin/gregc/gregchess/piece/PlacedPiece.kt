package gregc.gregchess.piece

import gregc.gregchess.*
import gregc.gregchess.move.connector.ChessboardView
import gregc.gregchess.registry.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

// TODO: remove this
@Serializable(with = PlacedPieceType.Serializer::class)
class PlacedPieceType<P : PlacedPiece>(val serializer: KSerializer<P>) : NameRegistered {
    @PublishedApi
    internal object Serializer : NameRegisteredSerializer<PlacedPieceType<*>>("PlacedPieceType", CoreRegistry.PLACED_PIECE_TYPE)

    override val key get() = CoreRegistry.PLACED_PIECE_TYPE[this]

    override fun toString(): String = CoreRegistry.PLACED_PIECE_TYPE.simpleElementToString(this)

    @RegisterAll(PlacedPieceType::class)
    companion object {
        internal val AUTO_REGISTER = AutoRegisterType(PlacedPieceType::class) { m, n, _ -> CoreRegistry.PLACED_PIECE_TYPE[m, n] = this }

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
    infix fun conflictsWith(p: PlacedPiece): Boolean
}

internal fun PlacedPiece?.boardPiece() = this as BoardPiece
internal fun PlacedPiece?.capturedPiece() = this as CapturedPiece

@PublishedApi
internal object PlacedPieceSerializer : KeyRegisteredSerializer<PlacedPieceType<*>, PlacedPiece>("PlacedPiece",
    PlacedPieceType.Serializer
) {

    @Suppress("UNCHECKED_CAST")
    override fun PlacedPieceType<*>.valueSerializer(module: SerializersModule): KSerializer<PlacedPiece> =
        serializer as KSerializer<PlacedPiece>

    override val PlacedPiece.key: PlacedPieceType<*> get() = placedPieceType
}

@Serializable
data class CapturedPiece(override val piece: Piece, val capturedBy: Color) : PlacedPiece {
    override val placedPieceType get() = PlacedPieceType.CAPTURED
    override fun conflictsWith(p: PlacedPiece): Boolean = false
}

@Serializable
data class BoardPiece(val pos: Pos, override val piece: Piece, val hasMoved: Boolean) : PlacedPiece {
    override val placedPieceType get() = PlacedPieceType.BOARD

    fun getMoves(board: ChessboardView) = board.getMoves(pos)
    fun getLegalMoves(board: ChessboardView) = board.getLegalMoves(pos)

    fun move(target: Pos) = this to this.copy(pos = target, hasMoved = true)
    fun capture(by: Color) = this to CapturedPiece(piece, by)
    fun promote(promotion: Piece) = this to this.copy(piece = promotion)

    override fun conflictsWith(p: PlacedPiece): Boolean = this == p
}