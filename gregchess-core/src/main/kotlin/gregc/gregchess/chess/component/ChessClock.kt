@file:UseSerializers(DurationSerializer::class)

package gregc.gregchess.chess.component

import gregc.gregchess.DurationSerializer
import gregc.gregchess.chess.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.LocalDateTime
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration

@Serializable
data class TimeControl(
    val type: Type,
    val initialTime: Duration,
    val increment: Duration = Duration.ZERO
) {
    enum class Type(val usesIncrement: Boolean = true) {
        FIXED(false), INCREMENT, BRONSTEIN, SIMPLE
    }

    fun getPGN() = buildString {
        if (type == Type.SIMPLE) {
            append("1/", initialTime.inWholeSeconds)
        } else {
            append(initialTime.inWholeSeconds)
            if (increment != Duration.ZERO)
                append("+", increment.inWholeSeconds)
        }
    }

    companion object {

        fun parseOrNull(name: String): TimeControl? = Regex("""(\d+)\+(\d+)""").find(name)?.let {
            TimeControl(Type.INCREMENT, it.groupValues[1].toLong().minutes, it.groupValues[2].toInt().seconds)
        }
    }

}

@Serializable
data class ChessClockData(
    val timeControl: TimeControl,
    val timeRemaining: ByColor<Duration> = byColor(timeControl.initialTime),
    val currentTurnLength: Duration = Duration.ZERO
) : ComponentData<ChessClock> {
    override val componentClass: KClass<out ChessClock> get() = ChessClock::class

    override fun getComponent(game: ChessGame) = ChessClock(game, this)
}

class ChessClock(game: ChessGame, settings: ChessClockData) : Component(game) {
    private val time = mutableByColor { settings.timeRemaining[it] }
    val timeControl = settings.timeControl
    private var currentTurnLength = settings.currentTurnLength

    override val data get() = ChessClockData(timeControl, byColor { time[it] }, currentTurnLength)

    private var lastTime: LocalDateTime = LocalDateTime.now()

    private var started = false
    private var stopped = false

    @ChessEventHandler
    fun handleEvents(e: GameBaseEvent) {
        if (e == GameBaseEvent.START && timeControl.type == TimeControl.Type.FIXED) started = true
        else if (e == GameBaseEvent.SYNC) when (game.state) {
            ChessGame.State.INITIAL -> {
                started = false
                stopped = false
            }
            ChessGame.State.RUNNING -> {
                started = timeControl.type == TimeControl.Type.FIXED
                stopped = false
            }
            ChessGame.State.STOPPED, ChessGame.State.ERROR -> {
                started = true
                stopped = true
            }
        }
        else if (e == GameBaseEvent.STOP || e == GameBaseEvent.PANIC) stopped = true
        else if (e == GameBaseEvent.UPDATE) updateTimer()
    }

    fun addTimer(s: Color, d: Duration) {
        time[s] += d
    }

    fun setTimer(s: Color, d: Duration) {
        time[s] = d
    }

    fun timeRemaining(s: Color) = time[s]

    private fun updateTimer() {
        if (!started)
            return
        if (stopped)
            return
        val now = LocalDateTime.now()
        val dt = java.time.Duration.between(lastTime, now).toKotlinDuration()
        lastTime = now
        currentTurnLength += dt
        if (timeControl.type != TimeControl.Type.SIMPLE) {
            time[game.currentTurn] -= dt
        } else {
            time[game.currentTurn] -= maxOf(minOf(dt, currentTurnLength - timeControl.increment), Duration.ZERO)
        }
        for ((s, t) in time.toIndexedList())
            if (t.isNegative())
                game.variant.timeout(game, s)
    }

    @ChessEventHandler
    fun endTurn(e: TurnEvent) {
        if (e != TurnEvent.END)
            return
        val increment = if (started) timeControl.increment else Duration.ZERO
        if (!started)
            started = true
        val turn = game.currentTurn
        when (timeControl.type) {
            TimeControl.Type.FIXED -> {
                time[turn] = timeControl.initialTime
            }
            TimeControl.Type.INCREMENT -> {
                time[turn] += increment
            }
            TimeControl.Type.BRONSTEIN -> {
                time[turn] += minOf(increment, currentTurnLength)
            }
            TimeControl.Type.SIMPLE -> {
            }
        }
        currentTurnLength = Duration.ZERO
    }
}