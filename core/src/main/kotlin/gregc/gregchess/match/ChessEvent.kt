package gregc.gregchess.match

import gregc.gregchess.*
import gregc.gregchess.board.SetFenEvent
import gregc.gregchess.move.connector.*
import gregc.gregchess.player.ChessSideType
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

class ChessEventHandler<in T : ChessEvent>(
    val owner: ChessEventOwner,
    val callback: (T) -> Unit
)

class ChessEventManager : ChessEventCaller {
    private val anyHandlers = mutableListOf<ChessEventHandler<ChessEvent>>()
    private val handlers = mutableMapOf<ChessEventType<*>, MutableList<ChessEventHandler<ChessEvent>>>()

    override fun callEvent(event: ChessEvent) = with(MultiExceptionContext()) {
        (handlers[event.type].orEmpty() + anyHandlers).forEach {
            exec {
                it.callback(event)
            }
        }
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

interface ChessEventOwner

class ChessEventSubOwner(val owner: ChessEventOwner, val subOwner: Any) : ChessEventOwner {
    override fun toString(): String = "$owner -> $subOwner"
}
class ChessEventComponentOwner(val type: ComponentType<*>) : ChessEventOwner {
    override fun toString(): String = type.toString()
}
class ChessEventSideOwner(val type: ChessSideType<*>, val color: Color) : ChessEventOwner {
    override fun toString(): String = "$type ($color)"
}

class ChessEventRegistry(private val owner: ChessEventOwner, private val manager: ChessEventManager) {
    fun registerAny(handler: (ChessEvent) -> Unit) {
        manager.registerEventAny(ChessEventHandler(owner, handler))
    }

    fun <T: ChessEvent> register(eventType: ChessEventType<T>, handler: (T) -> Unit) {
        manager.registerEvent(eventType, ChessEventHandler(owner, handler))
    }

    fun <T: ChessEvent> registerR(eventType: ChessEventType<T>, handler: T.() -> Unit) = register(eventType, handler)

    fun <T: ChessEvent> registerE(event: T, handler: () -> Unit) = register(event.type) { if (it == event) handler() }

    fun subRegistry(subOwner: Any) = ChessEventRegistry(ChessEventSubOwner(owner, subOwner), manager)
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