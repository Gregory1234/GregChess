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

class ChessEventOrderConstraint(
    val runBeforeAll: Boolean = false,
    val runAfterAll: Boolean = false,
    val runBefore: Set<ChessEventOwner> = emptySet(),
    val runAfter: Set<ChessEventOwner> = emptySet()
)

class ChessEventHandler<in T : ChessEvent>(
    val owner: ChessEventOwner,
    val constraints: ChessEventOrderConstraint,
    val callback: (T) -> Unit
)

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

interface ChessEventOwner {
    fun isOf(parent: ChessEventOwner): Boolean = this == parent
}

class ChessEventSubOwner(val owner: ChessEventOwner, val subOwner: Any) : ChessEventOwner {
    override fun toString(): String = "$owner -> $subOwner"
    override fun equals(other: Any?): Boolean = other is ChessEventSubOwner && other.owner == owner && other.subOwner == subOwner
    override fun hashCode(): Int {
        var result = owner.hashCode()
        result = 31 * result + subOwner.hashCode()
        return result
    }
    override fun isOf(parent: ChessEventOwner): Boolean = super.isOf(parent) || owner.isOf(parent)
}
class ChessEventComponentOwner(val type: ComponentType<*>) : ChessEventOwner {
    override fun toString(): String = type.toString()
    override fun equals(other: Any?): Boolean = other is ChessEventComponentOwner && other.type == type
    override fun hashCode(): Int = type.hashCode()
}
class ChessEventSideOwner(val type: ChessSideType<*>, val color: Color) : ChessEventOwner {
    override fun toString(): String = "$type ($color)"
    override fun equals(other: Any?): Boolean = other is ChessEventSideOwner && other.type == type && other.color == color
    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + color.hashCode()
        return result
    }
}

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