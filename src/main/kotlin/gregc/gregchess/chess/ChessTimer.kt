package gregc.gregchess.chess

import gregc.gregchess.GregChessInfo
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import java.util.concurrent.TimeUnit
import kotlin.math.max

class ChessTimer(override val game: ChessGame, private val settings: Settings): ChessGame.Component {

    data class Settings(val initialTime: Long, val increment: Long): ChessGame.ComponentSettings {
        override fun getComponent(game: ChessGame) = ChessTimer(game, this)

        companion object {

            private fun fromMinutesAndSeconds(minutes: Long, increment: Long) =
                Settings(TimeUnit.MINUTES.toMillis(minutes), TimeUnit.SECONDS.toMillis(increment))

            fun init() {
                ChessGame.Settings.registerComponent("Timer", "Settings.Timer") {
                    fromMinutesAndSeconds(it.getLong("Initial"), it.getLong("Increment"))
                }
            }
        }
    }

    private var stopping = false
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

    fun refreshClock() {
        val whiteTime = getWhiteTime()
        val blackTime = getBlackTime()
        if (whiteTime < 0) timeout(ChessSide.WHITE)
        if (blackTime < 0) timeout(ChessSide.BLACK)
        game.displayClock(max(whiteTime, 0), max(blackTime, 0))
    }

    override fun start() {
        whiteStartTime = System.currentTimeMillis()
        blackStartTime = System.currentTimeMillis()
        whiteEndTime = System.currentTimeMillis() + whiteTimeDiff
        blackEndTime = System.currentTimeMillis() + blackTimeDiff
        object : BukkitRunnable() {
            override fun run() {
                if (stopping)
                    this.cancel()
                refreshClock()
            }
        }.runTaskTimer(GregChessInfo.plugin, 0L, 20L)
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
        refreshClock()
    }

    override fun stop() {
        stopping = true
    }

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
        refreshClock()
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
        refreshClock()
    }
}
