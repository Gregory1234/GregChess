package gregc.gregchess.event

import gregc.gregchess.OrderConstraint

interface ChessEvent {
}

class ChessEventHandler<in T : ChessEvent>(
    val listener: EventListener,
    val constraints: OrderConstraint<EventListener>,
    val callback: (T) -> Unit
)

enum class TurnEvent(val ending: Boolean) : ChessEvent {
    START(false), END(true), UNDO(true)
}

enum class ChessBaseEvent : ChessEvent {
    START,
    SYNC,
    RUNNING,
    UPDATE,
    STOP,
    PANIC,
    CLEAR
}

class ChessEventException(val event: ChessEvent, cause: Throwable? = null) : RuntimeException(event.toString(), cause)

interface ChessEventCaller {
    fun callEvent(event: ChessEvent)
}