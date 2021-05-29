package gregc.gregchess.chess.component

import gregc.gregchess.chess.*
import kotlin.reflect.KClass

interface Component{
    interface Settings<out T: Component> {
        fun getComponent(game: ChessGame): T
    }
}

enum class GameBaseEvent {
    INIT,
    START,
    UPDATE,
    SPECTATOR_JOIN,
    SPECTATOR_LEAVE,
    STOP,
    CLEAR,
    VERY_END,
    START_TURN,
    END_TURN,
    PRE_PREVIOUS_TURN,
    START_PREVIOUS_TURN,
    ADD_PLAYER,
    REMOVE_PLAYER,
    RESET_PLAYER,
    PANIC
}

enum class TimeModifier {
    EARLY,
    NORMAL,
    LATE
}

@Target(AnnotationTarget.FUNCTION)
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
annotation class GameEvent(vararg val value: GameBaseEvent, val mod: TimeModifier = TimeModifier.NORMAL, val relaxed: Boolean = false)
@Target(AnnotationTarget.FUNCTION)
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
annotation class GameEvents(vararg val events: GameEvent)

private inline fun <reified T : Component> T.runGameEvent(value: GameBaseEvent, mod: TimeModifier, vararg args: Any?) {
    this::class.java.methods
        .filter { m ->
            m.annotations.map { if (it is GameEvents) it.events else it}
                .any { it is GameEvent && value in it.value && it.mod == mod }
        }.forEach {
            try {
                if (it.parameterCount < args.size)
                    it.invoke(this, *(args.take(it.parameterCount).toTypedArray()))
                else
                    it.invoke(this, *args)
            } catch (e: Exception) {
                if (it.annotations.map { a -> if (a is GameEvents) a.events else a}
                        .none { a -> a is GameEvent && value in a.value && a.mod == mod && a.relaxed})
                    e.printStackTrace()
            }
        }
}

private fun Collection<Component>.runGameEvent(value: GameBaseEvent, vararg args: Any?) {
    forEach { it.runGameEvent(value, TimeModifier.EARLY, *args) }
    forEach { it.runGameEvent(value, TimeModifier.NORMAL, *args) }
    forEach { it.runGameEvent(value, TimeModifier.LATE, *args) }
}

fun Collection<Component>.allInit() = runGameEvent(GameBaseEvent.INIT)
fun Collection<Component>.allStart() = runGameEvent(GameBaseEvent.START)
fun Collection<Component>.allUpdate() = runGameEvent(GameBaseEvent.UPDATE)
fun Collection<Component>.allSpectatorJoin(p: HumanPlayer) = runGameEvent(GameBaseEvent.SPECTATOR_JOIN, p)
fun Collection<Component>.allSpectatorLeave(p: HumanPlayer) = runGameEvent(GameBaseEvent.SPECTATOR_LEAVE, p)
fun Collection<Component>.allStop() = runGameEvent(GameBaseEvent.STOP)
fun Collection<Component>.allClear() = runGameEvent(GameBaseEvent.CLEAR)
fun Collection<Component>.allVeryEnd() = runGameEvent(GameBaseEvent.VERY_END)
fun Collection<Component>.allStartTurn() = runGameEvent(GameBaseEvent.START_TURN)
fun Collection<Component>.allEndTurn() = runGameEvent(GameBaseEvent.END_TURN)
fun Collection<Component>.allPrePreviousTurn() = runGameEvent(GameBaseEvent.PRE_PREVIOUS_TURN)
fun Collection<Component>.allStartPreviousTurn() = runGameEvent(GameBaseEvent.START_PREVIOUS_TURN)
fun Collection<Component>.allAddPlayer(p: HumanPlayer) = runGameEvent(GameBaseEvent.ADD_PLAYER, p)
fun Collection<Component>.allRemovePlayer(p: HumanPlayer) = runGameEvent(GameBaseEvent.REMOVE_PLAYER, p)
fun Collection<Component>.allResetPlayer(p: HumanPlayer) = runGameEvent(GameBaseEvent.RESET_PLAYER, p)
fun Collection<Component>.allPanic(e: Exception) = runGameEvent(GameBaseEvent.PANIC, e)

class ComponentNotFoundException(cl: KClass<*>): Exception(cl.toString())