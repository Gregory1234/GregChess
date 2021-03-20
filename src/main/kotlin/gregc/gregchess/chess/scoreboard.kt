package gregc.gregchess.chess

import gregc.gregchess.ConfigManager
import gregc.gregchess.TimeManager
import gregc.gregchess.chatColor
import gregc.gregchess.seconds
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Team

class ScoreboardManager(private val game: ChessGame) {
    private val gameProperties = mutableListOf<GameProperty>()
    private val playerProperties = mutableListOf<PlayerProperty>()

    private val view = ConfigManager.getView("Component.Scoreboard")

    private val objective =
        game.arena.scoreboard.registerNewObjective("GregChess", "", view.getString("Title"))

    private var stopping = false

    operator fun plusAssign(p: GameProperty) {
        gameProperties += p
    }

    operator fun plusAssign(p: PlayerProperty) {
        playerProperties += p
    }

    fun start() {
        objective.displaySlot = DisplaySlot.SIDEBAR
        val l = gameProperties.size * 2 + 1 + playerProperties.size * 4 + 1
        var i = l
        gameProperties.forEach {
            it.team = objective.scoreboard?.registerNewTeam(it.name)
            objective.getScore(view.getFormatString("GeneralFormat", it.name)).score = i--
            it.team?.addEntry(chatColor("&r").repeat(i))
            objective.getScore(chatColor("&r").repeat(i)).score = i--
        }
        objective.getScore(chatColor("&r").repeat(i)).score = i--
        playerProperties.forEach {
            it.teamWhite = objective.scoreboard?.registerNewTeam(it.name + "White")
            objective.getScore(view.getFormatString("WhiteFormat", it.name)).score = i--
            it.teamWhite?.addEntry(chatColor("&r").repeat(i))
            objective.getScore(chatColor("&r").repeat(i)).score = i--
        }
        objective.getScore(chatColor("&r").repeat(i)).score = i--
        playerProperties.forEach {
            it.teamBlack = objective.scoreboard?.registerNewTeam(it.name + "Black")
            objective.getScore(view.getFormatString("BlackFormat", it.name)).score = i--
            it.teamBlack?.addEntry(chatColor("&r").repeat(i))
            objective.getScore(chatColor("&r").repeat(i)).score = i--
        }

        TimeManager.runTaskTimer(0.seconds, 0.1.seconds) {
            if (stopping)
                cancel()
            game.update()
            update()
        }
    }

    private fun update() {
        if (stopping)
            return
        gameProperties.forEach {
            it.team?.prefix = it()
        }
        playerProperties.forEach {
            it.teamWhite?.prefix = it(ChessSide.WHITE)
            it.teamBlack?.prefix = it(ChessSide.BLACK)
        }
    }

    fun stop() {
        if (stopping)
            return
        stopping = true
        game.arena.clearScoreboard()
    }
}

abstract class PlayerProperty(val name: String) {
    var teamWhite: Team? = null
    var teamBlack: Team? = null

    abstract operator fun invoke(s: ChessSide): String
}

abstract class GameProperty(val name: String) {
    var team: Team? = null

    abstract operator fun invoke(): String
}