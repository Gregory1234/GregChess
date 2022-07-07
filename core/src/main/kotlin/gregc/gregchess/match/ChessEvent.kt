package gregc.gregchess.match

import kotlin.reflect.full.isSupertypeOf
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.typeOf

interface ChessEvent

@Target(AnnotationTarget.FUNCTION)
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
annotation class ChessEventHandler

interface ChessListener {
    fun handleEvent(match: ChessMatch, e: ChessEvent) {
        for (f in this::class.members) {
            if (f.annotations.any { it is ChessEventHandler } && f.parameters.size == 3 &&
                f.parameters[1].type.isSupertypeOf(typeOf<ChessMatch>()) &&
                f.parameters[2].type.isSupertypeOf(e::class.starProjectedType)
            ) {
                f.call(this, match, e)
            }
        }
    }
}

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
