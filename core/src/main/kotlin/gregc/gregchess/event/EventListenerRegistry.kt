package gregc.gregchess.event

import gregc.gregchess.OrderConstraint

class EventListenerRegistry(private val listener: EventListener, private val manager: ChessEventManager) {
    fun registerAny(constraints: OrderConstraint<EventListener>, handler: (ChessEvent) -> Unit) {
        manager.registerEventAny(ChessEventHandler(listener, constraints, handler))
    }

    fun <T: ChessEvent> register(eventType: ChessEventType<T>, constraints: OrderConstraint<EventListener>, handler: (T) -> Unit) {
        manager.registerEvent(eventType, ChessEventHandler(listener, constraints, handler))
    }

    fun <T: ChessEvent> registerR(eventType: ChessEventType<T>, constraints: OrderConstraint<EventListener>, handler: T.() -> Unit) = register(eventType, constraints, handler)

    fun <T: ChessEvent> registerE(event: T, constraints: OrderConstraint<EventListener>, handler: () -> Unit) = register(event.type, constraints) { if (it == event) handler() }

    fun registerAny(handler: (ChessEvent) -> Unit) = registerAny(OrderConstraint(), handler)
    fun <T: ChessEvent> register(eventType: ChessEventType<T>, handler: (T) -> Unit) = register(eventType, OrderConstraint(), handler)
    fun <T: ChessEvent> registerR(eventType: ChessEventType<T>, handler: T.() -> Unit) = registerR(eventType, OrderConstraint(), handler)
    fun <T: ChessEvent> registerE(event: T, handler: () -> Unit) = registerE(event, OrderConstraint(), handler)

    fun subRegistry(subOwner: Any) = EventListenerRegistry(listener / subOwner, manager)
}