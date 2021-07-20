package gregc.gregchess.chess.component

import gregc.gregchess.chess.*
import java.lang.reflect.Method
import kotlin.reflect.KClass

interface Component {
    interface Settings<out T : Component> {
        fun getComponent(game: ChessGame): T
    }
}

enum class GameBaseEvent {
    PRE_INIT,
    INIT,
    START,
    BEGIN,
    UPDATE,
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

fun Collection<Component>.allPreInit() = runGameEvent(GameBaseEvent.PRE_INIT)
fun Collection<Component>.allInit() = runGameEvent(GameBaseEvent.INIT)
fun Collection<Component>.allStart() = runGameEvent(GameBaseEvent.START)
fun Collection<Component>.allBegin() = runGameEvent(GameBaseEvent.BEGIN)
fun Collection<Component>.allUpdate() = runGameEvent(GameBaseEvent.UPDATE)
fun Collection<Component>.allStop(reason: EndReason) = runGameEvent(GameBaseEvent.STOP, reason)
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

class ComponentNotFoundException(cl: KClass<out Component>) : Exception(cl.toString())
class ComponentSettingsNotFoundException(cl: KClass<out Component.Settings<*>>) : Exception(cl.toString())
class ComponentConfigNotFoundException(cl: KClass<out Component>) : Exception(cl.toString())

object ComponentConfig {
    private val values = mutableMapOf<KClass<out Component>, Any>()

    fun <T : Component> getAny(cl: KClass<T>) = values[cl]
    inline fun <reified T : Component> getAny() = getAny(T::class)
    fun <T : Component> requireAny(cl: KClass<T>) = getAny(cl) ?: throw ComponentConfigNotFoundException(cl)
    inline fun <reified T : Component> requireAny() = requireAny(T::class)
    inline fun <reified T : Component, reified R : Any> get() = getAny<T>() as? R
    inline fun <reified T : Component, reified R : Any> require() = requireAny<T>() as R
    operator fun set(cl: KClass<out Component>, v: Any) {
        values[cl] = v
    }
}