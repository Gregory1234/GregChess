@file:UseSerializers(DurationSerializer::class)

package gregc.gregchess.clock

import gregc.gregchess.*
import gregc.gregchess.component.Component
import gregc.gregchess.component.ComponentType
import gregc.gregchess.event.*
import gregc.gregchess.match.ChessMatch
import gregc.gregchess.utils.DurationSerializer
import gregc.gregchess.utils.between
import kotlinx.serialization.*
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

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
        if (type == Type.FIXED) {
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
class ChessClock private constructor(
    val timeControl: TimeControl,
    @SerialName("timeRemaining") private val timeRemaining_: MutableByColor<Duration>,
    @SerialName("currentTurnLength") private var currentTurnLength_: Duration
) : Component {
    constructor(
        timeControl: TimeControl,
        timeRemaining: ByColor<Duration> = byColor(timeControl.initialTime),
        currentTurnLength: Duration = Duration.ZERO
    ) : this(timeControl, mutableByColor { timeRemaining[it] }, currentTurnLength)

    override val type get() = ComponentType.CLOCK

    override fun init(match: ChessMatch, events: EventListenerRegistry) {
        lastTime = Instant.now(match.environment.clock)
        events.register<ChessBaseEvent> { handleBaseEvent(match, it) }
        events.register<TurnEvent> { handleTurnEvent(match, it) }
    }

    val timeRemaining: ByColor<Duration> get() = byColor { timeRemaining_[it] }
    val currentTurnLength: Duration get() = currentTurnLength_

    @Transient private lateinit var lastTime: Instant

    @Transient private var started = false
    @Transient private var stopped = false

    private fun handleBaseEvent(match: ChessMatch, e: ChessBaseEvent) {
        if (e == ChessBaseEvent.START && timeControl.type == TimeControl.Type.FIXED) started = true
        else if (e == ChessBaseEvent.SYNC) when (match.state) {
            ChessMatch.State.INITIAL -> {
                started = false
                stopped = false
            }
            ChessMatch.State.RUNNING -> {
                started = timeControl.type == TimeControl.Type.FIXED
                stopped = false
            }
            ChessMatch.State.STOPPED, ChessMatch.State.ERROR -> {
                started = true
                stopped = true
            }
        }
        else if (e == ChessBaseEvent.STOP || e == ChessBaseEvent.PANIC) stopped = true
        else if (e == ChessBaseEvent.UPDATE) updateTimer(match)
    }

    fun addTimer(s: Color, d: Duration) {
        timeRemaining_[s] += d
    }

    fun setTimer(s: Color, d: Duration) {
        timeRemaining_[s] = d
    }

    private fun updateTimer(match: ChessMatch) {
        if (!started)
            return
        if (stopped)
            return
        val now = Instant.now(match.environment.clock)
        val dt = Duration.between(lastTime, now)
        lastTime = now
        currentTurnLength_ += dt
        if (timeControl.type != TimeControl.Type.SIMPLE) {
            timeRemaining_[match.currentColor] -= dt
        } else {
            timeRemaining_[match.currentColor] -= maxOf(minOf(dt, currentTurnLength_ - timeControl.increment), Duration.ZERO)
        }
        for ((s, t) in timeRemaining_.toIndexedList())
            if (t.isNegative())
                match.variant.timeout(match, s)
    }

    private fun handleTurnEvent(match: ChessMatch, e: TurnEvent) {
        if (e != TurnEvent.END)
            return
        val increment = if (started) timeControl.increment else Duration.ZERO
        if (!started)
            started = true
        val turn = match.currentColor
        when (timeControl.type) {
            TimeControl.Type.FIXED -> {
                timeRemaining_[turn] = timeControl.initialTime
            }
            TimeControl.Type.INCREMENT -> {
                timeRemaining_[turn] += increment
            }
            TimeControl.Type.BRONSTEIN -> {
                timeRemaining_[turn] += minOf(increment, currentTurnLength_)
            }
            TimeControl.Type.SIMPLE -> {
            }
        }
        currentTurnLength_ = Duration.ZERO
    }
}

val ChessMatch.clock get() = components.get(ComponentType.CLOCK)