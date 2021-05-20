package gregc.gregchess.chess.component

interface Component

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
    REMOVE_PLAYER,
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
annotation class GameEvent(vararg val value: GameBaseEvent, val mod: TimeModifier = TimeModifier.NORMAL)
@Target(AnnotationTarget.FUNCTION)
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
annotation class GameEvents(vararg val events: GameEvent)

inline fun <reified T : Component> T.runGameEvent(value: GameBaseEvent, mod: TimeModifier, vararg args: Any?) {
    this::class.java.methods
        .filter { m ->
            m.annotations.map { if (it is GameEvents) it.events else it}
                .any { it is GameEvent && value in it.value && it.mod == mod }
        }.forEach {
            try {
                it.invoke(this, *args)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
}

fun Collection<Component>.runGameEvent(value: GameBaseEvent, vararg args: Any?) {
    forEach { it.runGameEvent(value, TimeModifier.EARLY, *args) }
    forEach { it.runGameEvent(value, TimeModifier.NORMAL, *args) }
    forEach { it.runGameEvent(value, TimeModifier.LATE, *args) }
}