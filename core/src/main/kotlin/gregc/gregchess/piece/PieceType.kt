package gregc.gregchess.piece

import gregc.gregchess.CoreRegistry
import gregc.gregchess.registry.*
import kotlinx.serialization.Serializable

@Serializable(with = PieceType.Serializer::class)
class PieceType(val char: Char) : NameRegistered {

    @PublishedApi
    internal object Serializer : NameRegisteredSerializer<PieceType>("PieceType", CoreRegistry.PIECE_TYPE)

    override val key get() = CoreRegistry.PIECE_TYPE[this]

    override fun toString(): String = CoreRegistry.PIECE_TYPE.simpleElementToString(this)

    @RegisterAll(PieceType::class)
    companion object {

        internal val AUTO_REGISTER = AutoRegisterType(PieceType::class) { m, n, _ -> CoreRegistry.PIECE_TYPE[m, n] = this }

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