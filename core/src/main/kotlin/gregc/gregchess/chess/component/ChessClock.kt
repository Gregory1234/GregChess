@file:UseSerializers(DurationSerializer::class)

package gregc.gregchess.chess.component

import gregc.gregchess.*
import gregc.gregchess.chess.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Duration
import java.time.LocalDateTime

@Serializable
data class TimeControl(
    val type: Type,
    val initialTime: Duration,
    val increment: Duration = 0.seconds
) {
    enum class Type(val usesIncrement: Boolean = true) {
        FIXED(false), INCREMENT, BRONSTEIN, SIMPLE
    }

    fun getPGN() = buildString {
        if (type == Type.SIMPLE) {
            append("1/", initialTime.seconds)
        } else {
            append(initialTime.seconds)
            if (!increment.isZero)
                append("+", increment.seconds)
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
    val timeRemaining: MutableBySides<Duration> = mutableBySides(timeControl.initialTime),
    var currentTurnLength: Duration = 0.seconds
) : ComponentData<ChessClock> {
    override fun getComponent(game: ChessGame) = ChessClock(game, this)
}

class ChessClock(game: ChessGame, override val data: ChessClockData) : Component(game) {
    private val time = data.timeRemaining
    private val timeControl = data.timeControl

    private var lastTime: LocalDateTime = LocalDateTime.now()

    private var started = false
    private var stopped = false

    @ChessEventHandler
    fun handleEvents(e: GameBaseEvent) {
        if (e == GameBaseEvent.START && timeControl.type == TimeControl.Type.FIXED) started = true
        else if (e == GameBaseEvent.STOP) stopped = true
        else if (e == GameBaseEvent.UPDATE) updateTimer()
    }

    private fun updateTimer() {
        if (!started)
            return
        if (stopped)
            return
        val now = LocalDateTime.now()
        val dt = Duration.between(now, lastTime)
        lastTime = now
        data.currentTurnLength += dt
        if (timeControl.type != TimeControl.Type.SIMPLE) {
            time[game.currentTurn] -= dt
        } else {
            time[game.currentTurn] -= maxOf(minOf(dt, data.currentTurnLength - timeControl.increment), 0.seconds)
        }
        for ((s, t) in time.toIndexedList())
            if (t.isNegative)
                game.variant.timeout(game, s)
    }

    @ChessEventHandler
    fun endTurn(e: TurnEvent) {
        if (e != TurnEvent.END)
            return
        val increment = if (started) timeControl.increment else 0.seconds
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
                time[turn] += minOf(increment, data.currentTurnLength)
            }
            TimeControl.Type.SIMPLE -> {
            }
        }
        data.currentTurnLength = 0.seconds
    }
}
