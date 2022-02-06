package gregc.gregchess.chess.move

import gregc.gregchess.ChessModule
import gregc.gregchess.register
import gregc.gregchess.registry.*
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable(with = ChessVariantOption.Serializer::class)
class ChessVariantOption<T : Any>(val cl: KClass<T>, val pgnNameFragment: (T) -> String?) : NameRegistered {

    object Serializer : NameRegisteredSerializer<ChessVariantOption<*>>("ChessVariantOption", Registry.VARIANT_OPTION)

    override val key get() = Registry.VARIANT_OPTION[this]

    override fun toString(): String = Registry.VARIANT_OPTION.simpleElementToString(this)

    @RegisterAll(ChessVariantOption::class)
    companion object {

        internal val AUTO_REGISTER = AutoRegisterType(ChessVariantOption::class) { m, n, _ -> register(m, n) }

        @JvmField
        val SIMPLE_CASTLING = ChessVariantOption(Boolean::class) { if (it) "SimpleCastling" else null }

        fun registerCore(module: ChessModule) = AutoRegister(module, listOf(AUTO_REGISTER)).registerAll<ChessVariantOption<*>>()
    }
}