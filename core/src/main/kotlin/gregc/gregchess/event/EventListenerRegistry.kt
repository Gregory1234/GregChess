package gregc.gregchess.event

import gregc.gregchess.OrderConstraint
import kotlin.reflect.KClass

class EventListenerRegistry(private val listener: EventListener, private val manager: ChessEventManager) {
    fun registerAny(constraints: OrderConstraint<EventListener>, handler: (ChessEvent) -> Unit) {
        manager.registerEventAny(ChessEventHandler(listener, constraints, handler))
    }

    fun <T: ChessEvent> register(eventType: KClass<T>, constraints: OrderConstraint<EventListener>, handler: (T) -> Unit) {
        manager.registerEvent(eventType, ChessEventHandler(listener, constraints, handler))
    }

    fun <T: ChessEvent> registerR(eventType: KClass<T>, constraints: OrderConstraint<EventListener>, handler: T.() -> Unit) = register(eventType, constraints, handler)

    fun <T: ChessEvent> registerE(event: T, constraints: OrderConstraint<EventListener>, handler: () -> Unit) = register(event::class, constraints) { if (it == event) handler() }

    fun registerAny(handler: (ChessEvent) -> Unit) = registerAny(OrderConstraint(), handler)
    fun <T: ChessEvent> register(eventType: KClass<T>, handler: (T) -> Unit) = register(eventType, OrderConstraint(), handler)
    fun <T: ChessEvent> registerR(eventType: KClass<T>, handler: T.() -> Unit) = registerR(eventType, OrderConstraint(), handler)
    fun <T: ChessEvent> registerE(event: T, handler: () -> Unit) = registerE(event, OrderConstraint(), handler)

    inline fun <reified T: ChessEvent> register(constraints: OrderConstraint<EventListener>, noinline handler: (T) -> Unit) = register(T::class, constraints, handler)
    inline fun <reified T: ChessEvent> registerR(constraints: OrderConstraint<EventListener>, noinline handler: T.() -> Unit) = registerR(T::class, constraints, handler)
    inline fun <reified T: ChessEvent> register(noinline handler: (T) -> Unit) = register(T::class, OrderConstraint(), handler)
    inline fun <reified T: ChessEvent> registerR(noinline handler: T.() -> Unit) = registerR(T::class, OrderConstraint(), handler)

    fun subRegistry(subOwner: Any) = EventListenerRegistry(listener / subOwner, manager)
}