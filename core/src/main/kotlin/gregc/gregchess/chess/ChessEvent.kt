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
    for (f in this::class.members) {
        if (f.annotations.any { it is ChessEventHandler } && f.parameters.size == 2 &&
            f.parameters[1].type.isSupertypeOf(e::class.starProjectedType)
        ) {
            f.call(this, e)
        }
    }
}