package gregc.gregchess.event

import gregc.gregchess.MultiExceptionContext
import kotlin.reflect.KClass

class ChessEventManager : ChessEventCaller {
    private val anyHandlers = mutableListOf<ChessEventHandler<ChessEvent>>()
    private val handlers = mutableMapOf<KClass<out ChessEvent>, MutableList<ChessEventHandler<ChessEvent>>>()

    override fun callEvent(event: ChessEvent) = with(MultiExceptionContext()) {
        val handlersToOrder = (handlers[event::class].orEmpty() + anyHandlers).toMutableList()

        repeat(256) {
            handlersToOrder.removeIf { h ->
                if (handlersToOrder.any { o -> h.constraints.runBefore.any { o.listener.isOf(it) } })
                    false
                else if (handlersToOrder.any { o -> o.constraints.runAfter.any { h.listener.isOf(it) } })
                    false
                else if (!h.constraints.runBeforeAll && handlersToOrder.any { it.constraints.runBeforeAll })
                    false
                else if (h.constraints.runAfterAll && handlersToOrder.any { !it.constraints.runAfterAll })
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
    fun <T: ChessEvent> registerEvent(eventType: KClass<T>, handler: ChessEventHandler<T>) {
        handlers.getOrPut(eventType, ::mutableListOf) += handler as ChessEventHandler<ChessEvent>
    }

    fun registry(listener: EventListener) = EventListenerRegistry(listener, this)
}