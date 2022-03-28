package gregc.gregchess.game

import kotlin.reflect.full.isSupertypeOf
import kotlin.reflect.full.starProjectedType

interface ChessEvent

@Target(AnnotationTarget.FUNCTION)
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
annotation class ChessEventHandler

interface ChessListener {
    fun handleEvent(e: ChessEvent) {
        for (f in this::class.members) {
            if (f.annotations.any { it is ChessEventHandler } && f.parameters.size == 2 &&
                f.parameters[1].type.isSupertypeOf(e::class.starProjectedType)
            ) {
                f.call(this, e)
            }
        }
    }
}

enum class TurnEvent(val ending: Boolean) : ChessEvent {
    START(false), END(true), UNDO(true)
}

enum class GameBaseEvent : ChessEvent {
    START,
    SYNC,
    RUNNING,
    UPDATE,
    STOP,
    PANIC,
    CLEAR
}

class ChessEventException(val event: ChessEvent, cause: Throwable? = null) : RuntimeException(event.toString(), cause)
