package gregc.gregchess

import gregc.gregchess.registry.*
import kotlinx.serialization.Serializable


@Serializable(with = ChessFlag.Serializer::class)
class ChessFlag(@JvmField val isActive: (Int) -> Boolean) : NameRegistered {
    @PublishedApi
    internal object Serializer : NameRegisteredSerializer<ChessFlag>("ChessFlag", CoreRegistry.FLAG)

    override val key get() = CoreRegistry.FLAG[this]

    override fun toString(): String = CoreRegistry.FLAG.simpleElementToString(this)

    @RegisterAll(ChessFlag::class)
    companion object {

        internal val AUTO_REGISTER = AutoRegisterType(ChessFlag::class) { m, n, _ -> CoreRegistry.FLAG[m, n] = this }

        @JvmField
        val EN_PASSANT = ChessFlag { it == 1 }

        fun registerCore(module: ChessModule) = AutoRegister(module, listOf(AUTO_REGISTER)).registerAll<ChessFlag>()
    }
}

typealias ChessFlagReference = Map.Entry<ChessFlag, List<Int>>
val ChessFlagReference.flagType get() = key
val ChessFlagReference.flagAges get() = value
val ChessFlagReference.flagAge get() = value.lastOrNull()
val ChessFlagReference.flagActive get() = flagAge?.let(flagType.isActive) ?: false