package gregc.gregchess.chess

import gregc.gregchess.GregChessInfo
import gregc.gregchess.info
import org.bukkit.scheduler.BukkitRunnable
import kotlin.math.max

class ChessTimer(private val game: ChessGame, initialTime: Long, private val increment: Long) {
    private var stopping = false
    private var whiteStartTime: Long = System.currentTimeMillis()
    private var blackStartTime: Long = System.currentTimeMillis()
    private var whiteTimeDiff: Long = initialTime
    private var blackTimeDiff: Long = initialTime
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
        else if (game.config.relaxedInsufficientMaterial
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

    fun start() {
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

    fun switchPlayer() {
        when (game.currentTurn) {
            ChessSide.WHITE -> {
                val time = System.currentTimeMillis()
                blackStartTime = time
                blackEndTime = time + blackTimeDiff
                whiteTimeDiff = whiteEndTime - time + increment
            }
            ChessSide.BLACK -> {
                val time = System.currentTimeMillis()
                whiteStartTime = time
                whiteEndTime = time + whiteTimeDiff
                blackTimeDiff = blackEndTime - time + increment
            }
        }
        refreshClock()
    }

    fun stop() {
        stopping = true
    }

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
}
