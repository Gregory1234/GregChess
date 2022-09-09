package gregc.gregchess.event

class ChessEventRegistry(private val owner: ChessEventOwner, private val manager: ChessEventManager) {
    fun registerAny(constraints: ChessEventOrderConstraint, handler: (ChessEvent) -> Unit) {
        manager.registerEventAny(ChessEventHandler(owner, constraints, handler))
    }

    fun <T: ChessEvent> register(eventType: ChessEventType<T>, constraints: ChessEventOrderConstraint, handler: (T) -> Unit) {
        manager.registerEvent(eventType, ChessEventHandler(owner, constraints, handler))
    }

    fun <T: ChessEvent> registerR(eventType: ChessEventType<T>, constraints: ChessEventOrderConstraint, handler: T.() -> Unit) = register(eventType, constraints, handler)

    fun <T: ChessEvent> registerE(event: T, constraints: ChessEventOrderConstraint, handler: () -> Unit) = register(event.type, constraints) { if (it == event) handler() }

    fun registerAny(handler: (ChessEvent) -> Unit) = registerAny(ChessEventOrderConstraint(), handler)
    fun <T: ChessEvent> register(eventType: ChessEventType<T>, handler: (T) -> Unit) = register(eventType, ChessEventOrderConstraint(), handler)
    fun <T: ChessEvent> registerR(eventType: ChessEventType<T>, handler: T.() -> Unit) = registerR(eventType, ChessEventOrderConstraint(), handler)
    fun <T: ChessEvent> registerE(event: T, handler: () -> Unit) = registerE(event, ChessEventOrderConstraint(), handler)

    fun subRegistry(subOwner: Any) = ChessEventRegistry(ChessEventSubOwner(owner, subOwner), manager)
}