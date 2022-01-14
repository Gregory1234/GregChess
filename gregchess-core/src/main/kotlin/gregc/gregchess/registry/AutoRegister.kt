package gregc.gregchess.registry

import gregc.gregchess.ChessModule
import gregc.gregchess.chess.ChessFlag
import gregc.gregchess.chess.EndReason
import gregc.gregchess.chess.move.MoveNameTokenType
import gregc.gregchess.chess.piece.PieceType
import gregc.gregchess.chess.variant.ChessVariants
import kotlin.reflect.KClass
import kotlin.reflect.full.isSuperclassOf

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Register(val name: String = "")

class AutoRegisterType<T : Any>(val cl: KClass<T>, val register: (T, ChessModule, String) -> Unit)

class AutoRegister(private val module: ChessModule, private val types: Collection<AutoRegisterType<*>>) {
    companion object {
        val basicTypes = listOf(
            PieceType.AUTO_REGISTER, EndReason.AUTO_REGISTER, ChessFlag.AUTO_REGISTER, MoveNameTokenType.AUTO_REGISTER, ChessVariants.AUTO_REGISTER
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun registerAll(cl: KClass<*>) {
        for (p in cl.java.declaredFields) {
            p.annotations.filterIsInstance<Register>().forEach { a ->
                (types.first { it.cl.isSuperclassOf(p.type.kotlin) } as AutoRegisterType<Any>)
                    .register(p.get(null), module, a.name.ifBlank { p.name.lowercase() })
            }
        }
    }

    inline fun <reified T : Any> registerAll() = registerAll(T::class)
}

