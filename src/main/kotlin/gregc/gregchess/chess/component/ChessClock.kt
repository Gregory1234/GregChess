package gregc.gregchess.chess.component

import gregc.gregchess.chess.ChessGame
import gregc.gregchess.chess.ChessSide
import gregc.gregchess.chess.PlayerProperty
import org.bukkit.entity.Player
import java.util.concurrent.TimeUnit

class ChessClock(override val game: ChessGame, private val settings: Settings): ChessGame.Component {

    data class Settings(val initialTime: Long, val increment: Long): ChessGame.ComponentSettings {
        override fun getComponent(game: ChessGame) = ChessClock(game, this)

        companion object {

            private fun fromMinutesAndSeconds(minutes: Long, increment: Long) =
                Settings(TimeUnit.MINUTES.toMillis(minutes), TimeUnit.SECONDS.toMillis(increment))

            fun init() {
                ChessGame.Settings.registerComponent("Clock", "Settings.Clock") {
                    fromMinutesAndSeconds(it.getLong("Initial"), it.getLong("Increment"))
                }
            }
        }
    }

    private var whiteStartTime: Long = System.currentTimeMillis()
    private var blackStartTime: Long = System.currentTimeMillis()
    private var whiteTimeDiff: Long = settings.initialTime
    private var blackTimeDiff: Long = settings.initialTime
    private var whiteEndTime: Long = System.currentTimeMillis() + whiteTimeDiff
    private var blackEndTime: Long = System.currentTimeMillis() + blackTimeDiff

    fun getWhiteTime() = when (game.currentTurn) {
        ChessSide.WHITE -> whiteEndTime - System.currentTimeMillis()
        ChessSide.BLACK -> whiteTimeDiff
    }

    fun getBlackTime() = when (game.currentTurn) {
        ChessSide.WHITE -> blackTimeDiff
        ChessSide.BLACK -> blackEndTime - System.currentTimeMillis()
    }

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
        whiteStartTime = System.currentTimeMillis()
        blackStartTime = System.currentTimeMillis()
        whiteEndTime = System.currentTimeMillis() + whiteTimeDiff
        blackEndTime = System.currentTimeMillis() + blackTimeDiff
        game.scoreboard += object : PlayerProperty("time") {

            override fun invoke(s: ChessSide) = when (s) {
                ChessSide.WHITE -> format(getWhiteTime())
                ChessSide.BLACK -> format(getBlackTime())
            }

            private fun format(time: Long) =
                "%02d:%02d.%d".format(TimeUnit.MILLISECONDS.toMinutes(time), TimeUnit.MILLISECONDS.toSeconds(time) % 60, (time/100)%10)
        }
    }

    override fun update() {
        val whiteTime = getWhiteTime()
        val blackTime = getBlackTime()
        if (whiteTime < 0) timeout(ChessSide.WHITE)
        if (blackTime < 0) timeout(ChessSide.BLACK)
    }

    override fun endTurn() {
        when (game.currentTurn) {
            ChessSide.WHITE -> {
                val time = System.currentTimeMillis()
                blackStartTime = time
                blackEndTime = time + blackTimeDiff
                whiteTimeDiff = whiteEndTime - time + settings.increment
            }
            ChessSide.BLACK -> {
                val time = System.currentTimeMillis()
                whiteStartTime = time
                whiteEndTime = time + whiteTimeDiff
                blackTimeDiff = blackEndTime - time + settings.increment
            }
        }
    }

    override fun stop() {}

    override fun spectatorJoin(p: Player) {}
    override fun spectatorLeave(p: Player) {}

    override fun startTurn() {}
    override fun clear() {}

    fun addTime(side: ChessSide, addition: Long) {
        when (side) {
            ChessSide.WHITE -> {
                whiteEndTime += addition
                whiteTimeDiff += addition
            }
            ChessSide.BLACK -> {
                blackEndTime += addition
                blackTimeDiff += addition
            }
        }
    }

    fun setTime(side: ChessSide, seconds: Long) {
        when (side) {
            ChessSide.WHITE -> {
                whiteStartTime = System.currentTimeMillis()
                whiteEndTime = whiteStartTime + seconds
                whiteTimeDiff = seconds
            }
            ChessSide.BLACK -> {
                blackStartTime = System.currentTimeMillis()
                blackEndTime = blackStartTime + seconds
                blackTimeDiff += seconds
            }
        }
    }
}
