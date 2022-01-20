package gregc.gregchess.chess.component

import gregc.gregchess.ClassMapSerializer
import gregc.gregchess.chess.ChessGame
import gregc.gregchess.chess.ChessListener
import gregc.gregchess.registry.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import kotlin.reflect.KClass
import kotlin.reflect.full.createType

// TODO: allow for components to own a reference to ChessGame?
interface Component : ChessListener {

    fun init(game: ChessGame) {}

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