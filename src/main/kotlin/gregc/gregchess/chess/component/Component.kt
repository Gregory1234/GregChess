package gregc.gregchess.chess.component

import gregc.gregchess.glog

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
    START_PREVIOUS_TURN
}

enum class TimeModifier {
    EARLY,
    NORMAL,
    LATE
}

@Target(AnnotationTarget.FUNCTION)
annotation class GameEvent(val value: GameBaseEvent, val mod: TimeModifier = TimeModifier.NORMAL)

inline fun <reified T : Component> T.runGameEvent(value: GameBaseEvent, mod: TimeModifier, vararg args: Any?) {
    this::class.java.methods
        .filter { m -> m.annotations.any { it is GameEvent && it.value == value && it.mod == mod } }
        .forEach { try { glog.debug(it.name, this.javaClass.name, *args); it.invoke(this, *args)} catch (e: Exception) {e.printStackTrace()} }
}

fun Collection<Component>.runGameEvent(value: GameBaseEvent, vararg args: Any?) {
    forEach { it.runGameEvent(value, TimeModifier.EARLY, *args) }
    forEach { it.runGameEvent(value, TimeModifier.NORMAL, *args) }
    forEach { it.runGameEvent(value, TimeModifier.LATE, *args) }
}