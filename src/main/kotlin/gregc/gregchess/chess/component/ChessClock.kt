package gregc.gregchess.chess.component

import gregc.gregchess.chess.*
import gregc.gregchess.minutes
import gregc.gregchess.parseDuration
import gregc.gregchess.seconds
import org.bukkit.entity.Player
import java.lang.Long.max
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.ceil

class ChessClock(override val game: ChessGame, private val settings: Settings) : ChessGame.Component {

    enum class Type(val usesIncrement: Boolean = true) {
        FIXED(false), INCREMENT, BRONSTEIN, SIMPLE
    }

    data class Settings(val type: Type, val initialTime: Duration, val increment: Duration = 0.seconds) :
        ChessGame.ComponentSettings {
        override fun getComponent(game: ChessGame) = ChessClock(game, this)

        companion object {

            fun init(settingsManager: SettingsManager) {
                settingsManager.registerComponent("Clock", "Settings.Clock") {
                    val t = Type.valueOf(it.getString("Type")?.toUpperCase() ?: "INCREMENT")
                    Settings(
                        t,
                        parseDuration(it.getString("Initial").orEmpty()) ?: 0.seconds,
                        parseDuration(it.getString("Increment") ?: "0") ?: 0.seconds
                    )
                }
            }
        }
    }

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

    private var whiteTime = Time(settings.initialTime)
    private var blackTime = Time(settings.initialTime)

    private fun getTime(s: ChessSide) = when (s) {
        ChessSide.WHITE -> whiteTime
        ChessSide.BLACK -> blackTime
    }

    private var started = false
    private var stopTime: LocalDateTime? = null

    fun getTimeRemaining(s: ChessSide) =
        getTime(s).getRemaining(s == game.currentTurn && started, stopTime ?: LocalDateTime.now())

    private fun timeout(side: ChessSide) {
        if (game.board.piecesOf(!side).size == 1)
            game.stop(ChessGame.EndReason.DrawTimeout())
        else if (game.settings.relaxedInsufficientMaterial
            && game.board.piecesOf(!side).size == 2 && game.board.piecesOf(!side).any { it.type.minor }
        )
            game.stop(ChessGame.EndReason.DrawTimeout())
        else
            game.stop(ChessGame.EndReason.Timeout(!side))
    }

    override fun start() {


        if (settings.type == Type.FIXED) {
            game.scoreboard += object : GameProperty("Time remaining") {

                override fun invoke() = format(ceil(getTimeRemaining(game.currentTurn).toNanos()*0.000001).toLong())

                private fun format(time: Long) =
                    "%02d:%02d.%d".format(
                        max((time / 1000 / 60), 0),
                        max((time / 1000) % 60, 0),
                        max((time / 100) % 10, 0)
                    )
            }
            startTimer()
        }
        else {
            game.scoreboard += object : PlayerProperty("time") {

                override fun invoke(s: ChessSide) = format(ceil(getTimeRemaining(s).toNanos()*0.000001).toLong())

                private fun format(time: Long) =
                    "%02d:%02d.%d".format(
                        max((time / 1000 / 60), 0),
                        max((time / 1000) % 60, 0),
                        max((time / 100) % 10, 0)
                    )
            }
        }
    }

    private fun startTimer() {
        ChessSide.values().forEach { getTime(it).reset() }
        if (settings.type == Type.FIXED) {
            getTime(game.currentTurn).begin = LocalDateTime.now() + settings.increment
            getTime(game.currentTurn) += settings.increment
        }
        started = true
    }

    override fun update() {
        ChessSide.values().forEach { if (getTimeRemaining(it).isNegative) timeout(it) }
    }

    override fun endTurn() {
        val increment = if (started) settings.increment else 0.seconds
        if (!started)
            startTimer()
        val time = LocalDateTime.now()
        val turn = game.currentTurn
        getTime(!turn).start = time
        getTime(!turn).end = time + getTime(!turn).diff
        when (settings.type) {
            Type.FIXED -> {
                getTime(turn).diff = settings.initialTime
            }
            Type.INCREMENT -> {
                getTime(turn).diff = Duration.between(time, getTime(turn).end + increment)
            }
            Type.BRONSTEIN -> {
                val reset = Duration.between(getTime(turn).start, time)
                getTime(turn).diff =
                    Duration.between(time, getTime(turn).end + if (increment > reset) reset else increment)
            }
            Type.SIMPLE -> {
                getTime(!turn) += increment
                getTime(!turn).begin = time + increment
                getTime(turn).diff = Duration.between(
                    if (getTime(turn).begin == null || time > getTime(turn).begin) time else getTime(turn).begin,
                    getTime(turn).end
                )
            }
        }
    }

    override fun startPreviousTurn() {}
    override fun previousTurn() {}

    override fun stop() {
        stopTime = LocalDateTime.now()
    }

    override fun spectatorJoin(p: Player) {}
    override fun spectatorLeave(p: Player) {}

    override fun startTurn() {}
    override fun clear() {}

    fun addTime(side: ChessSide, addition: Duration) {
        getTime(side) += addition
    }

    fun setTime(side: ChessSide, seconds: Duration) {
        getTime(side).set(seconds)
    }
}
