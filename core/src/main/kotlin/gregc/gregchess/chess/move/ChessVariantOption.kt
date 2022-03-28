package gregc.gregchess.chess.move

import gregc.gregchess.ChessModule
import gregc.gregchess.registry.NameRegistered
import gregc.gregchess.registry.Registry
import gregc.gregchess.util.*

class ChessVariantOption<T>(val pgnNameFragment: (T) -> String?) : NameRegistered {

    override val key get() = Registry.VARIANT_OPTION[this]

    override fun toString(): String = Registry.VARIANT_OPTION.simpleElementToString(this)

    @RegisterAll(ChessVariantOption::class)
    companion object {

        internal val AUTO_REGISTER = AutoRegisterType(ChessVariantOption::class) { m, n, _ -> Registry.VARIANT_OPTION[m, n] = this }

        @JvmField
        val SIMPLE_CASTLING = ChessVariantOption<Boolean> { if (it) "SimpleCastling" else null }

        fun registerCore(module: ChessModule) = AutoRegister(module, listOf(AUTO_REGISTER)).registerAll<ChessVariantOption<*>>()
    }
}