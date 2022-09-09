package gregc.gregchess.component

import gregc.gregchess.SelfType
import gregc.gregchess.event.ChessEvent
import gregc.gregchess.event.ChessEventRegistry
import gregc.gregchess.match.AnyFacade
import gregc.gregchess.match.ChessMatch
import gregc.gregchess.registry.KeyRegisteredSerializer
import kotlinx.serialization.*
import kotlinx.serialization.modules.SerializersModule
import kotlin.reflect.full.createType

fun interface ComponentIdentifier<T : Component> {
    fun matchesOrNull(c: Component): T?
}

@Serializable(with = ComponentSerializer::class)
interface Component {

    val type: ComponentType<out @SelfType Component>

    fun init(match: ChessMatch, events: ChessEventRegistry) {}

}

object ComponentSerializer : KeyRegisteredSerializer<ComponentType<*>, Component>("Component", ComponentType.Serializer) {

    @Suppress("UNCHECKED_CAST")
    override fun ComponentType<*>.valueSerializer(module: SerializersModule): KSerializer<Component> =
        module.serializer(cl.createType()) as KSerializer<Component>

    override val Component.key: ComponentType<*> get() = type

}

class ComponentNotFoundException(type: ComponentIdentifier<*>) : Exception(type.toString())

abstract class ComponentFacade<T : Component>(final override val match: ChessMatch, val component: T) : AnyFacade {
    @Suppress("UNCHECKED_CAST")
    val type: ComponentType<T> get() = component.type as ComponentType<T>

    final override fun callEvent(event: ChessEvent) = super.callEvent(event)
}