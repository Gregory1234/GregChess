package gregc.gregchess.util

import gregc.gregchess.ChessModule
import gregc.gregchess.registry.*
import kotlinx.serialization.Serializable


@Serializable(with = ChessFlag.Serializer::class)
class ChessFlag(@JvmField val isActive: (Int) -> Boolean) : NameRegistered {
    object Serializer : NameRegisteredSerializer<ChessFlag>("ChessFlag", Registry.FLAG)

    override val key get() = Registry.FLAG[this]

    override fun toString(): String = Registry.FLAG.simpleElementToString(this)

    @RegisterAll(ChessFlag::class)
    companion object {

        internal val AUTO_REGISTER = AutoRegisterType(ChessFlag::class) { m, n, _ -> Registry.FLAG[m, n] = this }

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