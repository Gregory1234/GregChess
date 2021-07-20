package gregc.gregchess.chess

import gregc.gregchess.chess.component.Component
import kotlin.reflect.full.isSupertypeOf
import kotlin.reflect.full.starProjectedType

interface ChessEvent

@Target(AnnotationTarget.FUNCTION)
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
annotation class ChessEventHandler

fun Component.callEvent(e: ChessEvent) {
    this::class.members.forEach { f ->
        if (f.annotations.any { it is ChessEventHandler } && f.parameters.size == 2 && f.parameters[1].type.isSupertypeOf(e::class.starProjectedType)) {
            f.call(this, e)
        }
    }
}

fun Collection<Component>.callEvent(e: ChessEvent) = forEach { it.callEvent(e) }