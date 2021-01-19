package gregc.gregchess.chess

import gregc.gregchess.GregChessInfo
import gregc.gregchess.chatColor
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scoreboard.DisplaySlot

class ScoreboardManager(val game: ChessGame) {
    private val gameProperties = mutableListOf<GameProperty>()
    private val playerProperties = mutableListOf<PlayerProperty>()

    private var stopping = false

    operator fun plusAssign(p: GameProperty) {
        gameProperties += p
    }

    operator fun plusAssign(p: PlayerProperty) {
        playerProperties += p
    }

    fun start() {
        object : BukkitRunnable() {
            override fun run() {
                if (stopping)
                    cancel()
                game.update()
                update()
            }
        }.runTaskTimer(GregChessInfo.plugin, 0L, 2L)
    }

    private fun update() {
        game.arena.clearScoreboard()
        val objective = game.arena.scoreboard.registerNewObjective("GregChess", "", "GregChess game")
        objective.displaySlot = DisplaySlot.SIDEBAR
        val lines = mutableListOf<String>()

        gameProperties.forEach {
            lines += "${it.name}:"
            lines += it()
        }
        lines += ""
        playerProperties.forEach {
            lines += "White ${it.name}:"
            lines += it(ChessSide.WHITE)
        }
        lines += ""
        playerProperties.forEach {
            lines += "Black ${it.name}:"
            lines += it(ChessSide.BLACK)
        }
        val counts = mutableMapOf<String, Int>()
        val l = lines.size
        lines.forEachIndexed { i, line ->
            val postfix = " ".repeat(counts[line] ?: 0)
            counts[line] = (counts[line] ?: 0) + 1
            objective.getScore(line + postfix).score = l - i
        }

    }

    fun stop() {
        stopping = true
    }
}

abstract class PlayerProperty(val name: String) {
    abstract operator fun invoke(s: ChessSide): String
}

abstract class GameProperty(val name: String) {
    abstract operator fun invoke(): String
}