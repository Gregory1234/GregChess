package gregc.gregchess.piece

import gregc.gregchess.*
import gregc.gregchess.registry.*
import kotlinx.serialization.Serializable

@Serializable(with = PieceType.Serializer::class)
class PieceType(val char: Char) : NameRegistered {

    object Serializer : NameRegisteredSerializer<PieceType>("PieceType", Registry.PIECE_TYPE)

    override val key get() = Registry.PIECE_TYPE[this]

    override fun toString(): String = Registry.PIECE_TYPE.simpleElementToString(this)

    @RegisterAll(PieceType::class)
    companion object {

        internal val AUTO_REGISTER = AutoRegisterType(PieceType::class) { m, n, _ -> Registry.PIECE_TYPE[m, n] = this }

        @JvmField
        val KING = PieceType('k')
        @JvmField
        val QUEEN = PieceType('q')
        @JvmField
        val ROOK = PieceType('r')
        @JvmField
        val BISHOP = PieceType('b')
        @JvmField
        val KNIGHT = PieceType('n')
        @JvmField
        val PAWN = PieceType('p')

        fun registerCore(module: ChessModule) = AutoRegister(module, listOf(AUTO_REGISTER)).registerAll<PieceType>()

        fun chooseByChar(values: Collection<PieceType>, c: Char): PieceType =
            requireNotNull(values.firstOrNull { it.char == c.lowercaseChar() }) { "None of the pieces have character: '$c'" }
    }
}