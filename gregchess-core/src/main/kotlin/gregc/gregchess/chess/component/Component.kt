package gregc.gregchess.chess.component

import gregc.gregchess.ClassMapSerializer
import gregc.gregchess.chess.*
import gregc.gregchess.registry.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import kotlin.reflect.KClass
import kotlin.reflect.full.*
import kotlin.reflect.typeOf

// TODO: allow for components to own a reference to ChessGame?
interface Component {

    fun validate(game: ChessGame) {}

    fun handleEvent(game: ChessGame, e: ChessEvent) {
        for (f in this::class.members) {
            if (f.annotations.any { it is ChessEventHandler } && f.parameters.size == 3 &&
                f.parameters[1].type == typeOf<ChessGame>() &&
                f.parameters[2].type.isSupertypeOf(e::class.starProjectedType)
            ) {
                f.call(this, game, e)
            }
        }
    }
}

object ComponentMapSerializer : ClassMapSerializer<Map<RegistryKey<String>, Component>, RegistryKey<String>, Component>("ComponentDataMap", StringKeySerializer) {

    override fun Map<RegistryKey<String>, Component>.asMap() = this

    override fun fromMap(m: Map<RegistryKey<String>, Component>) = m

    @Suppress("UNCHECKED_CAST")
    override fun RegistryKey<String>.valueSerializer(module: SerializersModule) = module.serializer(Registry.COMPONENT_CLASS[this].createType()) as KSerializer<Component>

}

val KClass<out Component>.componentKey get() = Registry.COMPONENT_CLASS[this]
val KClass<out Component>.componentModule get() = componentKey.module
val KClass<out Component>.componentName get() = componentKey.key

class ComponentNotFoundException(cl: KClass<out Component>) : Exception(cl.toString())