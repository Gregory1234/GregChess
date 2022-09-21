package gregc.gregchess.event

import gregc.gregchess.SelfType
import gregc.gregchess.board.SetFenEvent
import gregc.gregchess.match.PGN
import gregc.gregchess.move.connector.*
import gregc.gregchess.player.HumanRequestEvent
import gregc.gregchess.stats.AddStatsEvent

interface ChessEvent {
    val type: ChessEventType<out @SelfType ChessEvent>
}

// TODO: attempt to remove this
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
        @JvmField
        val HUMAN_REQUEST = ChessEventType<HumanRequestEvent>()
    }
}

data class ChessEventOrderConstraint(
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