package gregc.gregchess.component

import gregc.gregchess.SelfType
import gregc.gregchess.event.ChessEvent
import gregc.gregchess.event.EventListenerRegistry
import gregc.gregchess.match.AnyFacade
import gregc.gregchess.match.ChessMatch

interface Component {

    val type: ComponentType<out @SelfType Component>

    fun init(match: ChessMatch, events: EventListenerRegistry) {}

}

abstract class ComponentFacade<T : Component>(final override val match: ChessMatch, val component: T) : AnyFacade {
    @Suppress("UNCHECKED_CAST")
    val type: ComponentType<T> get() = component.type as ComponentType<T>

    final override fun callEvent(event: ChessEvent) = super.callEvent(event)
}