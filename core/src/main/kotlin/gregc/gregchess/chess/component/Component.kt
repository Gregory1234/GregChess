package gregc.gregchess.chess.component

import gregc.gregchess.*
import gregc.gregchess.chess.*
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable(with = ComponentDataSerializer::class)
interface ComponentData<out T : Component> {
    fun getComponent(game: ChessGame): T
}

abstract class Component(protected val game: ChessGame) : ChessListener, ChessEventCaller {
    abstract val data: ComponentData<*>

    open fun validate() {}

    final override fun toString(): String = buildString {
        val cl = this@Component::class
        append(cl.componentKey)
        append("@")
        append(cl.hashCode())
        append("(game.uuid=")
        append(game.uuid)
        append(", data=")
        append(data)
        append(")")
    }

    final override fun callEvent(e: ChessEvent) = game.callEvent(e)
}

object ComponentDataSerializer : ClassRegisteredSerializer<ComponentData<*>>("ComponentData", COMPONENT_DATA_CLASS_VIEW)

private val COMPONENT_DATA_CLASS_VIEW =
    DoubleChainRegistryView(RegistryType.COMPONENT_CLASS, RegistryType.COMPONENT_DATA_CLASS)

val KClass<out Component>.componentDataClass get() = RegistryType.COMPONENT_DATA_CLASS[componentModule, this]
val KClass<out Component>.componentKey get() = RegistryType.COMPONENT_CLASS[this]
val KClass<out Component>.componentModule get() = componentKey.module
val KClass<out Component>.componentName get() = componentKey.key

val KClass<out ComponentData<*>>.componentClass get() = componentDataKey.key
val KClass<out ComponentData<*>>.componentDataKey get() = RegistryType.COMPONENT_DATA_CLASS[this]

val KClass<out ComponentData<*>>.componentKey
    @JvmName("getComponentDataDoubleKey") get() = COMPONENT_DATA_CLASS_VIEW[this]
val KClass<out ComponentData<*>>.componentModule
    @JvmName("getComponentDataModule") get() = componentDataKey.module
val KClass<out ComponentData<*>>.componentName
    @JvmName("getComponentDataName") get() = componentKey.key

class ComponentNotFoundException(cl: KClass<out Component>) : Exception(cl.toString())