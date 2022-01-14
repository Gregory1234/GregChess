package gregc.gregchess.chess.piece

import gregc.gregchess.ChessModule
import gregc.gregchess.register
import gregc.gregchess.registry.*
import kotlinx.serialization.Serializable

@Serializable(with = PieceType.Serializer::class)
class PieceType(val char: Char) : NameRegistered {

    object Serializer : NameRegisteredSerializer<PieceType>("PieceType", Registry.PIECE_TYPE)

    override val key get() = Registry.PIECE_TYPE[this]

    override fun toString(): String = Registry.PIECE_TYPE.simpleElementToString(this)

    companion object {

        internal val AUTO_REGISTER = AutoRegisterType(PieceType::class) { m, n, _ -> register(m, n) }

        @JvmField
        @Register
        val KING = PieceType('k')
        @JvmField
        @Register
        val QUEEN = PieceType('q')
        @JvmField
        @Register
        val ROOK = PieceType('r')
        @JvmField
        @Register
        val BISHOP = PieceType('b')
        @JvmField
        @Register
        val KNIGHT = PieceType('n')
        @JvmField
        @Register
        val PAWN = PieceType('p')

        fun registerCore(module: ChessModule) = AutoRegister(module, listOf(AUTO_REGISTER)).registerAll<PieceType>()

        fun chooseByChar(values: Collection<PieceType>, c: Char): PieceType =
            requireNotNull(values.firstOrNull { it.char == c.lowercaseChar() }) { "None of the pieces have character: '$c'" }
    }
}