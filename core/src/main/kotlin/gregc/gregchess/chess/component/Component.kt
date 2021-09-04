package gregc.gregchess.chess.component

import gregc.gregchess.*
import gregc.gregchess.chess.ChessGame
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable(with = ComponentDataSerializer::class)
interface ComponentData<out T : Component> {
    fun getComponent(game: ChessGame): T
}
abstract class Component(protected val game: ChessGame) {
    abstract val data: ComponentData<*>

    open fun validate() {}

    final override fun toString(): String = buildString {
        val cl = this@Component::class
        append(cl.componentModule.namespace)
        append(":")
        append(cl.componentName)
        append("@")
        append(cl.hashCode())
        append("(game.uuid=")
        append(game.uuid)
        append(", data=")
        append(data)
        append(")")
    }
}

object ComponentDataSerializer: ClassRegisteredSerializer<ComponentData<*>>("ComponentData", COMPONENT_DATA_CLASS_VIEW)

private val COMPONENT_DATA_CLASS_VIEW = DoubleChainRegistryView(RegistryType.COMPONENT_CLASS, RegistryType.COMPONENT_DATA_CLASS)

val KClass<out Component>.componentDataClass get() = RegistryType.COMPONENT_DATA_CLASS[componentModule, this]
val KClass<out Component>.componentModule get() = RegistryType.COMPONENT_CLASS.getModule(this)
val KClass<out Component>.componentName get() = RegistryType.COMPONENT_CLASS[this]

val KClass<out ComponentData<*>>.componentClass get() = RegistryType.COMPONENT_DATA_CLASS[this]
@get:JvmName("getComponentDataModule")
val KClass<out ComponentData<*>>.componentModule get() = RegistryType.COMPONENT_DATA_CLASS.getModule(this)
@get:JvmName("getComponentDataName")
val KClass<out ComponentData<*>>.componentName get() = COMPONENT_DATA_CLASS_VIEW[this]

class ComponentNotFoundException(cl: KClass<out Component>) : Exception(cl.toString())