package gregc.gregchess.chess.component

import gregc.gregchess.ConfigManager
import gregc.gregchess.chess.*
import gregc.gregchess.glog
import gregc.gregchess.minutes
import gregc.gregchess.seconds
import java.lang.Long.max
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.ceil


class ChessClock(private val game: ChessGame, private val settings: Settings) : Component {

    enum class Type(val usesIncrement: Boolean = true) {
        FIXED(false), INCREMENT, BRONSTEIN, SIMPLE
    }

    data class Settings(
        val type: Type, val initialTime: Duration, val increment: Duration = 0.seconds
    ) {

        fun getPGN() = buildString {
            if (type == Type.SIMPLE) {
                append("1/", initialTime.seconds)
            } else {
                append(initialTime.seconds)
                if (!increment.isZero)
                    append("+", increment.seconds)
            }
        }

        fun getComponent(game: ChessGame) = ChessClock(game, this)

        companion object {

            operator fun get(name: String?) = when (name) {
                "none" -> null
                null -> null
                else -> {
                    val settings = SettingsManager.parseSettings("Clock") {
                        val t = it.getEnum("Type", Type.INCREMENT, Type::class, false)
                        Settings(
                            t, it.getDuration("Initial"),
                            it.getDuration("Increment", t.usesIncrement)
                        )
                    }
                    if (name in settings)
                        settings[name]
                    else {
                        val match = Regex("""(\d+)\+(\d+)""").find(name)
                        if (match != null) {
                            Settings(
                                Type.INCREMENT,
                                match.groupValues[1].toLong().minutes,
                                match.groupValues[2].toInt().seconds
                            )
                        } else {
                            glog.warn("Invalid chessboard configuration $name, defaulted to none!")
                            null
                        }
                    }
                }
            }
        }
    }

    private val view
        get() = ConfigManager.getView("Component.Clock")

    data class Time(
        var diff: Duration,
        var start: LocalDateTime = LocalDateTime.now(),
        var end: LocalDateTime = LocalDateTime.now() + diff,
        var begin: LocalDateTime? = null
    ) {
        fun reset() {
            start = LocalDateTime.now()
            end = LocalDateTime.now() + diff
        }

        operator fun plusAssign(addition: Duration) {
            diff += addition
            end += addition
        }

        fun set(time: Duration) {
            diff = time
            end = LocalDateTime.now() + time
        }

        fun getRemaining(isActive: Boolean, time: LocalDateTime): Duration = if (isActive) {
            Duration.between(if (begin == null || time > begin) time else begin, end)
        } else {
            diff
        }
    }

    private var time = BySides(Time(settings.initialTime), Time(settings.initialTime))

    private var started = false
    private var stopTime: LocalDateTime? = null

    fun getTimeRemaining(s: ChessSide) =
        time[s].getRemaining(s == game.currentTurn && started, stopTime ?: LocalDateTime.now())

    private fun format(time: Duration): String {
        val formatter = DateTimeFormatter.ofPattern(view.getString("TimeFormat"))
        return (LocalTime.ofNanoOfDay(
            max(ceil(time.toNanos().toDouble() / 1000000.0).toLong() * 1000000, 0)
        )).format(formatter)
    }

    @GameEvent(GameBaseEvent.START)
    fun start() {

        if (settings.type == Type.FIXED) {
            game.scoreboard += object : GameProperty(view.getString("TimeRemainingSimple")) {
                override fun invoke() = format(getTimeRemaining(game.currentTurn))
            }
            startTimer()
        } else {
            game.scoreboard += object : PlayerProperty(view.getString("TimeRemaining")) {
                override fun invoke(s: ChessSide) = format(getTimeRemaining(s))
            }
        }
    }

    private fun startTimer() {
        ChessSide.values().forEach { time[it].reset() }
        if (settings.type == Type.FIXED) {
            time[game.currentTurn].begin = LocalDateTime.now() + settings.increment
            time[game.currentTurn] += settings.increment
        }
        started = true
    }

    @GameEvent(GameBaseEvent.UPDATE)
    fun update() {
        ChessSide.values().forEach {
            if (getTimeRemaining(it).isNegative) game.variant.timeout(game, it)
        }
    }

    @GameEvent(GameBaseEvent.END_TURN)
    fun endTurn() {
        val increment = if (started) settings.increment else 0.seconds
        if (!started)
            startTimer()
        val now = LocalDateTime.now()
        val turn = game.currentTurn
        time[!turn].start = now
        time[!turn].end = now + time[!turn].diff
        when (settings.type) {
            Type.FIXED -> {
                time[turn].diff = settings.initialTime
            }
            Type.INCREMENT -> {
                time[turn].diff = Duration.between(now, time[turn].end + increment)
            }
            Type.BRONSTEIN -> {
                val reset = Duration.between(time[turn].start, now)
                time[turn].diff =
                    Duration.between(
                        now,
                        time[turn].end + if (increment > reset) reset else increment
                    )
            }
            Type.SIMPLE -> {
                time[!turn] += increment
                time[!turn].begin = now + increment
                time[turn].diff = Duration.between(
                    if (time[turn].begin == null || now > time[turn].begin) now
                    else time[turn].begin,
                    time[turn].end
                )
            }
        }
    }

    @GameEvent(GameBaseEvent.STOP)
    fun stop() {
        stopTime = LocalDateTime.now()
    }

    fun addTime(side: ChessSide, addition: Duration) {
        time[side] += addition
    }

    fun setTime(side: ChessSide, seconds: Duration) {
        time[side].set(seconds)
    }
}
