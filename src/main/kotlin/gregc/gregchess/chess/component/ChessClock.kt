package gregc.gregchess.chess.component

import gregc.gregchess.chess.ChessGame
import gregc.gregchess.chess.ChessSide
import gregc.gregchess.chess.PlayerProperty
import gregc.gregchess.chess.SettingsManager
import org.bukkit.entity.Player
import java.lang.Long.max
import java.lang.Long.min
import java.util.concurrent.TimeUnit

class ChessClock(override val game: ChessGame, private val settings: Settings) : ChessGame.Component {

    enum class Type(val usesIncrement: Boolean = true) {
        FIXED(false), INCREMENT, BRONSTEIN, SIMPLE
    }

    data class Settings(val type: Type, val initialTime: Long, val increment: Long = 0) : ChessGame.ComponentSettings {
        override fun getComponent(game: ChessGame) = ChessClock(game, this)

        companion object {

            fun init(settingsManager: SettingsManager) {
                settingsManager.registerComponent("Clock", "Settings.Clock") {
                    val t = Type.valueOf(it.getString("Type")?.toUpperCase() ?: "INCREMENT")
                    Settings(
                        t,
                        TimeUnit.MINUTES.toMillis(it.getLong("Initial")),
                        if (t.usesIncrement) TimeUnit.SECONDS.toMillis(it.getLong("Increment")) else 0
                    )
                }
            }
        }
    }

    data class Time(
        var diff: Long,
        var start: Long = System.currentTimeMillis(),
        var end: Long = System.currentTimeMillis() + diff,
        var begin: Long? = null
    ) {
        fun reset() {
            start = System.currentTimeMillis()
            end = System.currentTimeMillis() + diff
        }

        operator fun plusAssign(addition: Long) {
            diff += addition
            end += addition
        }

        fun set(time: Long) {
            diff = time
            end = System.currentTimeMillis() + time
        }

        fun getRemaining(isActive: Boolean, time: Long) = if (isActive) {
            end - max(time, begin ?: 0)
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
    private var stopTime: Long? = null

    fun getTimeRemaining(s: ChessSide) =
        getTime(s).getRemaining(s == game.currentTurn && started, stopTime ?: System.currentTimeMillis())

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
        game.scoreboard += object : PlayerProperty("time") {

            override fun invoke(s: ChessSide) = format(max(getTimeRemaining(s), 0))

            private fun format(time: Long) =
                "%02d:%02d.%d".format(
                    TimeUnit.MILLISECONDS.toMinutes(time),
                    TimeUnit.MILLISECONDS.toSeconds(time) % 60,
                    (time / 100) % 10
                )
        }
        if (settings.type == Type.SIMPLE)
            startTimer()
    }

    private fun startTimer() {
        ChessSide.values().forEach { getTime(it).reset() }
        if (settings.type == Type.SIMPLE) {
            getTime(game.currentTurn).begin = System.currentTimeMillis() + settings.increment
            getTime(game.currentTurn) += settings.increment
        }
        started = true
    }

    override fun update() {
        ChessSide.values().forEach { if (getTimeRemaining(it) < 0) timeout(it) }
    }

    override fun endTurn() {
        val increment = if (started) settings.increment else 0
        if (!started)
            startTimer()
        val time = System.currentTimeMillis()
        val turn = game.currentTurn
        getTime(!turn).start = time
        getTime(!turn).end = time + getTime(!turn).diff
        when (settings.type) {
            Type.FIXED -> {
                getTime(turn).diff = settings.initialTime
            }
            Type.INCREMENT -> {
                getTime(turn).diff = getTime(turn).end - time + increment
            }
            Type.BRONSTEIN -> {
                getTime(turn).diff = getTime(turn).end - time + min(increment, time - getTime(turn).start)
            }
            Type.SIMPLE -> {
                getTime(!turn) += increment
                getTime(!turn).begin = time + increment
                getTime(turn).diff = getTime(turn).end - max(time, getTime(turn).begin ?: 0)
            }
        }
    }

    override fun startPreviousTurn() {}
    override fun previousTurn() {}

    override fun stop() {
        stopTime = System.currentTimeMillis()
    }

    override fun spectatorJoin(p: Player) {}
    override fun spectatorLeave(p: Player) {}

    override fun startTurn() {}
    override fun clear() {}

    fun addTime(side: ChessSide, addition: Long) {
        getTime(side) += addition
    }

    fun setTime(side: ChessSide, seconds: Long) {
        getTime(side).set(seconds)
    }
}
