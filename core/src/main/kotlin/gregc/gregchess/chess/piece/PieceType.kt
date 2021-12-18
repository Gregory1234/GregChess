package gregc.gregchess.chess.piece

import gregc.gregchess.GregChess
import gregc.gregchess.register
import gregc.gregchess.registry.*
import kotlinx.serialization.Serializable

@Serializable(with = PieceType.Serializer::class)
class PieceType(
    val char: Char
) : NameRegistered {

    object Serializer : NameRegisteredSerializer<PieceType>("PieceType", RegistryType.PIECE_TYPE)

    override val key get() = RegistryType.PIECE_TYPE[this]

    override fun toString(): String = "$key@${hashCode().toString(16)}"

    companion object {

        @JvmField
        val KING = GregChess.register("king", PieceType('k'))
        @JvmField
        val QUEEN = GregChess.register("queen", PieceType('q'))
        @JvmField
        val ROOK = GregChess.register("rook", PieceType('r'))
        @JvmField
        val BISHOP = GregChess.register("bishop", PieceType('b'))
        @JvmField
        val KNIGHT = GregChess.register("knight", PieceType('n'))
        @JvmField
        val PAWN = GregChess.register("pawn", PieceType('p'))

        fun chooseByChar(values: Collection<PieceType>, c: Char): PieceType =
            requireNotNull(values.firstOrNull { it.char == c.lowercaseChar() }) { "None of the pieces have character: '$c'" }
    }
}