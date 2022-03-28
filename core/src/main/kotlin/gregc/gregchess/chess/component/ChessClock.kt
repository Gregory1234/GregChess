@file:UseSerializers(DurationSerializer::class)

package gregc.gregchess.chess.component

import gregc.gregchess.chess.*
import gregc.gregchess.util.DurationSerializer
import gregc.gregchess.util.between
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

    @Transient
    private lateinit var game: ChessGame

    override fun init(game: ChessGame) {
        this.game = game
        lastTime = Instant.now(game.environment.clock)
    }

    val timeRemaining: ByColor<Duration> get() = byColor { timeRemaining_[it] }
    val currentTurnLength: Duration get() = currentTurnLength_

    @Transient private lateinit var lastTime: Instant

    @Transient private var started = false
    @Transient private var stopped = false

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
        timeRemaining_[s] += d
    }

    fun setTimer(s: Color, d: Duration) {
        timeRemaining_[s] = d
    }

    private fun updateTimer() {
        if (!started)
            return
        if (stopped)
            return
        val now = Instant.now(game.environment.clock)
        val dt = Duration.between(lastTime, now)
        lastTime = now
        currentTurnLength_ += dt
        if (timeControl.type != TimeControl.Type.SIMPLE) {
            timeRemaining_[game.currentTurn] -= dt
        } else {
            timeRemaining_[game.currentTurn] -= maxOf(minOf(dt, currentTurnLength_ - timeControl.increment), Duration.ZERO)
        }
        for ((s, t) in timeRemaining_.toIndexedList())
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

val ComponentHolder.clock get() = get(ComponentType.CLOCK)