package gregc.gregchess.chess

import gregc.gregchess.GregChessInfo
import gregc.gregchess.info
import org.bukkit.scheduler.BukkitRunnable

class ChessTimer(private val game: ChessGame, initialTime: Long, private val increment: Long) {
    private var stopping = false
    private var whiteStartTime: Long = System.currentTimeMillis()
    private var blackStartTime: Long = System.currentTimeMillis()
    private var whiteTimeDiff: Long = initialTime
    private var blackTimeDiff: Long = initialTime
    private var whiteEndTime: Long = System.currentTimeMillis()+whiteTimeDiff
    private var blackEndTime: Long = System.currentTimeMillis()+blackTimeDiff

    fun getWhiteTime() = when (game.currentTurn){
        ChessSide.WHITE -> whiteEndTime - System.currentTimeMillis()
        ChessSide.BLACK -> whiteTimeDiff
    }

    fun getBlackTime() = when (game.currentTurn){
        ChessSide.WHITE -> blackTimeDiff
        ChessSide.BLACK -> blackEndTime - System.currentTimeMillis()
    }

    fun start() {
        whiteStartTime = System.currentTimeMillis()
        blackStartTime = System.currentTimeMillis()
        whiteEndTime = System.currentTimeMillis()+whiteTimeDiff
        blackEndTime = System.currentTimeMillis()+blackTimeDiff
        object: BukkitRunnable() {
            override fun run() {
                if (stopping)
                    this.cancel()
                val whiteTime = getWhiteTime()
                val blackTime = getBlackTime()
                if (whiteTime < 0) game.stop(ChessGame.EndReason.Timeout(ChessSide.BLACK))
                if (blackTime < 0) game.stop(ChessGame.EndReason.Timeout(ChessSide.WHITE))
                game.displayClock(whiteTime, blackTime)
            }
        }.runTaskTimer(GregChessInfo.plugin, 0L, 20L)
    }

    fun switchPlayer() {
        when (game.currentTurn) {
            ChessSide.WHITE -> {
                val time = System.currentTimeMillis()
                if (whiteEndTime < time)
                    game.stop(ChessGame.EndReason.Timeout(ChessSide.BLACK))
                blackStartTime = time
                blackEndTime = time + blackTimeDiff
                whiteTimeDiff = whiteEndTime - time + increment
            }
            ChessSide.BLACK -> {
                val time = System.currentTimeMillis()
                if (blackEndTime < time)
                    game.stop(ChessGame.EndReason.Timeout(ChessSide.WHITE))
                whiteStartTime = time
                whiteEndTime = time + whiteTimeDiff
                blackTimeDiff = blackEndTime - time + increment
            }
        }
    }

    fun stop() {
        stopping = true
    }
}
