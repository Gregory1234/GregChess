package gregc.gregchess.chess.piece

import gregc.gregchess.GregChess
import gregc.gregchess.registerPieceType
import gregc.gregchess.registry.*
import kotlinx.serialization.Serializable

@Serializable(with = PieceType.Serializer::class)
class PieceType(val char: Char) : NameRegistered {

    object Serializer : NameRegisteredSerializer<PieceType>("PieceType", Registry.PIECE_TYPE)

    override val key get() = Registry.PIECE_TYPE[this]

    override fun toString(): String = Registry.PIECE_TYPE.simpleElementToString(this)

    companion object {

        @JvmField
        val KING = GregChess.registerPieceType("king", PieceType('k'))
        @JvmField
        val QUEEN = GregChess.registerPieceType("queen", PieceType('q'))
        @JvmField
        val ROOK = GregChess.registerPieceType("rook", PieceType('r'))
        @JvmField
        val BISHOP = GregChess.registerPieceType("bishop", PieceType('b'))
        @JvmField
        val KNIGHT = GregChess.registerPieceType("knight", PieceType('n'))
        @JvmField
        val PAWN = GregChess.registerPieceType("pawn", PieceType('p'))

        fun chooseByChar(values: Collection<PieceType>, c: Char): PieceType =
            requireNotNull(values.firstOrNull { it.char == c.lowercaseChar() }) { "None of the pieces have character: '$c'" }
    }
}