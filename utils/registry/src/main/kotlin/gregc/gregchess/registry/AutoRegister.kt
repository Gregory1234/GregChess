package gregc.gregchess.registry

import kotlin.reflect.KClass
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.isSuperclassOf

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Register(val name: String = "", val data: Array<String> = [])
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RegisterAll(vararg val classes: KClass<*>)

class AutoRegisterType<T : Any>(val cl: KClass<T>, val register: T.(ChessModule, String, Collection<String>) -> Unit)

// TODO: make it possible to auto register to non-name registries
// TODO: make it possible to auto register functions
class AutoRegister(private val module: ChessModule, private val types: Collection<AutoRegisterType<*>>) {

    @Suppress("UNCHECKED_CAST")
    fun registerAll(cl: KClass<*>) {
        val regAll = cl.annotations.filterIsInstance<RegisterAll>().firstOrNull()
            ?: cl.companionObject?.annotations?.filterIsInstance<RegisterAll>()?.firstOrNull()
        for (p in cl.java.nestHost.declaredFields.ifEmpty { cl.java.declaringClass.declaredFields }) {
            val reg = p.annotations.filterIsInstance<Register>().firstOrNull()
            if (reg != null || regAll != null && regAll.classes.any { it.isSuperclassOf(p.type.kotlin) }) {
                (types.first { it.cl.isSuperclassOf(p.type.kotlin) } as AutoRegisterType<Any>)
                    .register(p.get(null), module, (reg?.name ?: "").ifBlank { p.name.lowercase() }, reg?.data?.toList().orEmpty())
            }
        }
    }

    inline fun <reified T : Any> registerAll() = registerAll(T::class)
}