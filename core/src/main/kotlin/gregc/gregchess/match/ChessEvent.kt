package gregc.gregchess.match

import gregc.gregchess.MultiExceptionContext
import gregc.gregchess.SelfType
import gregc.gregchess.board.SetFenEvent
import gregc.gregchess.move.connector.*
import gregc.gregchess.stats.AddStatsEvent

interface ChessEvent {
    val type: ChessEventType<out @SelfType ChessEvent>
}

class ChessEventType<T> {
    companion object {
        @JvmField
        val TURN = ChessEventType<TurnEvent>()
        @JvmField
        val BASE = ChessEventType<ChessBaseEvent>()
        @JvmField
        val SET_FEN = ChessEventType<SetFenEvent>()
        @JvmField
        val ADD_MOVE_CONNECTORS = ChessEventType<AddMoveConnectorsEvent>()
        @JvmField
        val ADD_FAKE_MOVE_CONNECTORS = ChessEventType<AddFakeMoveConnectorsEvent>()
        @JvmField
        val GENERATE_PGN = ChessEventType<PGN.GenerateEvent>()
        @JvmField
        val ADD_STATS = ChessEventType<AddStatsEvent>()
        @JvmField
        val PIECE_MOVE = ChessEventType<PieceMoveEvent>()
    }
}

class ChessEventManager : ChessEventCaller {
    private val anyHandlers = mutableListOf<(ChessEvent) -> Unit>()
    private val handlers = mutableMapOf<ChessEventType<*>, MutableList<(ChessEvent) -> Unit>>()

    override fun callEvent(event: ChessEvent) = with(MultiExceptionContext()) {
        (handlers[event.type].orEmpty() + anyHandlers).forEach {
            exec {
                it.invoke(event)
            }
        }
        rethrow { ChessEventException(event, it) }
    }

    fun registerEventAny(handler: (ChessEvent) -> Unit) {
        anyHandlers += handler
    }

    @Suppress("UNCHECKED_CAST")
    fun <T: ChessEvent> registerEvent(eventType: ChessEventType<T>, handler: (T) -> Unit) {
        handlers.getOrPut(eventType, ::mutableListOf) += (handler as (ChessEvent) -> Unit)
    }

    fun <T: ChessEvent> registerEventR(eventType: ChessEventType<T>, handler: T.() -> Unit) = registerEvent(eventType, handler)

    fun <T: ChessEvent> registerEventE(event: T, handler: () -> Unit) = registerEvent(event.type) { if (it == event) handler() }
}

enum class TurnEvent(val ending: Boolean) : ChessEvent {
    START(false), END(true), UNDO(true);

    override val type get() = ChessEventType.TURN
}

enum class ChessBaseEvent : ChessEvent {
    START,
    SYNC,
    RUNNING,
    UPDATE,
    STOP,
    PANIC,
    CLEAR;

    override val type get() = ChessEventType.BASE
}

class ChessEventException(val event: ChessEvent, cause: Throwable? = null) : RuntimeException(event.toString(), cause)

interface ChessEventCaller {
    fun callEvent(event: ChessEvent)
}