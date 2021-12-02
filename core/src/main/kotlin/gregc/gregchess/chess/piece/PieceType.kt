package gregc.gregchess.chess.piece

import gregc.gregchess.GregChessModule
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

        private fun register(id: String, type: PieceType) = GregChessModule.register(id, type)

        @JvmField
        val KING = register("king", PieceType('k'))
        @JvmField
        val QUEEN = register("queen", PieceType('q'))
        @JvmField
        val ROOK = register("rook", PieceType('r'))
        @JvmField
        val BISHOP = register("bishop", PieceType('b'))
        @JvmField
        val KNIGHT = register("knight", PieceType('n'))
        @JvmField
        val PAWN = register("pawn", PieceType('p'))

        fun chooseByChar(values: Collection<PieceType>, c: Char): PieceType =
            requireNotNull(values.firstOrNull { it.char == c.lowercaseChar() }) { "None of the pieces have character: '$c'" }
    }
}