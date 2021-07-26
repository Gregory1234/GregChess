package gregc.gregchess.chess

import gregc.gregchess.GregChess
import gregc.gregchess.ident
import net.minecraft.util.Identifier

val PieceType.id get() = FabricPieceTypes.getId(this)!!

val Piece.block get() = GregChess.PIECE_BLOCKS[this]!!
val Piece.item get() = GregChess.PIECE_ITEMS[this]!!
val Piece.id get() = type.id.let { Identifier(it.namespace, side.name.lowercase() + "_" + it.path) }

val Floor.chess get() = ChessboardFloor.valueOf(name)

object FabricPieceTypes {
    private val pieceTypes = mutableMapOf<Identifier, PieceType>()

    fun register(id: Identifier, pieceType: PieceType) {
        if (id in pieceTypes)
            throw IllegalArgumentException(id.toString())
        if (pieceTypes.containsValue(pieceType))
            throw IllegalArgumentException(pieceType.toString())
        pieceTypes[id] = pieceType
    }

    init {
        register(ident("king"), PieceType.KING)
        register(ident("queen"), PieceType.QUEEN)
        register(ident("rook"), PieceType.ROOK)
        register(ident("bishop"), PieceType.BISHOP)
        register(ident("knight"), PieceType.KNIGHT)
        register(ident("pawn"), PieceType.PAWN)
    }

    fun getId(pieceType: PieceType) = pieceTypes.filterValues { it == pieceType }.keys.firstOrNull()
    operator fun get(id: Identifier) = pieceTypes[id]

    val values get() = pieceTypes.values
    val ids get() = pieceTypes.keys
}