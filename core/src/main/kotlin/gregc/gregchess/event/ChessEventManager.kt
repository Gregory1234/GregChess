package gregc.gregchess.event

import gregc.gregchess.MultiExceptionContext

class ChessEventManager : ChessEventCaller {
    private val anyHandlers = mutableListOf<ChessEventHandler<ChessEvent>>()
    private val handlers = mutableMapOf<ChessEventType<*>, MutableList<ChessEventHandler<ChessEvent>>>()

    override fun callEvent(event: ChessEvent) = with(MultiExceptionContext()) {
        val handlersToOrder = (handlers[event.type].orEmpty() + anyHandlers).toMutableList()

        repeat(256) {
            handlersToOrder.removeIf { h ->
                if (handlersToOrder.any { o -> h.constraints.runBefore.any { o.owner.isOf(it) } })
                    false
                else if (handlersToOrder.any { o -> o.constraints.runAfter.any { h.owner.isOf(it) } })
                    false
                else if (!h.constraints.runBeforeAll && handlersToOrder.any { h.constraints.runBeforeAll })
                    false
                else if (h.constraints.runAfterAll && handlersToOrder.any { !h.constraints.runAfterAll })
                    false
                else {
                    exec {
                        h.callback(event)
                    }
                    true
                }
            }
        }
        if (handlersToOrder.isNotEmpty())
            throw ChessEventException(event)
        rethrow { ChessEventException(event, it) }
    }

    fun registerEventAny(handler: ChessEventHandler<ChessEvent>) {
        anyHandlers += handler
    }

    @Suppress("UNCHECKED_CAST")
    fun <T: ChessEvent> registerEvent(eventType: ChessEventType<T>, handler: ChessEventHandler<T>) {
        handlers.getOrPut(eventType, ::mutableListOf) += handler as ChessEventHandler<ChessEvent>
    }

    fun registry(owner: ChessEventOwner) = ChessEventRegistry(owner, this)
}