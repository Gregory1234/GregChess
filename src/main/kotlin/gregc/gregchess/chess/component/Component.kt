package gregc.gregchess.chess.component

import gregc.gregchess.glog
import org.bukkit.entity.Player

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

private inline fun <reified T : Component> T.runGameEvent(value: GameBaseEvent, mod: TimeModifier, vararg args: Any?) {
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

private fun Collection<Component>.runGameEvent(value: GameBaseEvent, vararg args: Any?) {
    forEach { it.runGameEvent(value, TimeModifier.EARLY, *args) }
    forEach { it.runGameEvent(value, TimeModifier.NORMAL, *args) }
    forEach { it.runGameEvent(value, TimeModifier.LATE, *args) }
}

fun Collection<Component>.init() = runGameEvent(GameBaseEvent.INIT)
fun Collection<Component>.start() = runGameEvent(GameBaseEvent.START)
fun Collection<Component>.update() = runGameEvent(GameBaseEvent.UPDATE)
fun Collection<Component>.spectatorJoin(p: Player) = runGameEvent(GameBaseEvent.SPECTATOR_JOIN, p)
fun Collection<Component>.spectatorLeave(p: Player) = runGameEvent(GameBaseEvent.SPECTATOR_LEAVE, p)
fun Collection<Component>.stop() = runGameEvent(GameBaseEvent.STOP)
fun Collection<Component>.clearing() = runGameEvent(GameBaseEvent.CLEAR)
fun Collection<Component>.veryEnd() = runGameEvent(GameBaseEvent.VERY_END)
fun Collection<Component>.startTurn() = runGameEvent(GameBaseEvent.START_TURN)
fun Collection<Component>.endTurn() = runGameEvent(GameBaseEvent.END_TURN)
fun Collection<Component>.prePreviousTurn() = runGameEvent(GameBaseEvent.PRE_PREVIOUS_TURN)
fun Collection<Component>.startPreviousTurn() = runGameEvent(GameBaseEvent.START_PREVIOUS_TURN)
fun Collection<Component>.removePlayer(p: Player) = runGameEvent(GameBaseEvent.REMOVE_PLAYER, p)
fun Collection<Component>.panic(e: Exception) = runGameEvent(GameBaseEvent.PANIC, e)