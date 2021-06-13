package gregc.gregchess.chess.component

import gregc.gregchess.chess.ChessGame
import gregc.gregchess.chess.HumanPlayer
import java.lang.reflect.Method
import kotlin.reflect.KClass

interface Component {
    interface Settings<out T : Component> {
        fun getComponent(game: ChessGame): T
    }
}

enum class GameBaseEvent {
    INIT,
    START,
    BEGIN,
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
annotation class GameEvent(
    vararg val value: GameBaseEvent,
    val mod: TimeModifier = TimeModifier.NORMAL,
    val relaxed: Boolean = false
)

@Target(AnnotationTarget.FUNCTION)
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
annotation class GameEvents(vararg val events: GameEvent)

private val Method.gameEvents
    get() = annotations.map { if (it is GameEvents) it.events else it }.filterIsInstance<GameEvent>()

private inline fun <reified T : Component> T.runGameEvent(value: GameBaseEvent, mod: TimeModifier, vararg args: Any?) {
    this::class.java.methods
        .filter { m -> m.gameEvents.any { value in it.value && it.mod == mod } }
        .forEach { m ->
            try {
                if (m.parameterCount < args.size)
                    m.invoke(this, *(args.take(m.parameterCount).toTypedArray()))
                else
                    m.invoke(this, *args)
            } catch (e: Exception) {
                if (m.gameEvents.none { value in it.value && it.mod == mod && it.relaxed })
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
fun Collection<Component>.allBegin() = runGameEvent(GameBaseEvent.BEGIN)
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

class ComponentNotFoundException(cl: KClass<*>) : Exception(cl.toString())