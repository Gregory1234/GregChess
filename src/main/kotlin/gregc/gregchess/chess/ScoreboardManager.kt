package gregc.gregchess.chess

import gregc.gregchess.*
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Objective
import org.bukkit.scoreboard.Team

class ScoreboardManager(private val game: ChessGame): ChessGame.Component {
    private val gameProperties = mutableListOf<GameProperty>()
    private val playerProperties = mutableListOf<PlayerProperty>()

    private val view = ConfigManager.getView("Component.Scoreboard")

    private lateinit var objective: Objective

    operator fun plusAssign(p: GameProperty) {
        gameProperties += p
    }

    operator fun plusAssign(p: PlayerProperty) {
        playerProperties += p
    }

    private fun newTeam(): Team {
        var s: String
        do {
            s = randomString(16)
        } while (game.renderer.scoreboard.getTeam(s) != null)
        return game.renderer.scoreboard.registerNewTeam(s)
    }

    override fun start() {
        objective = game.renderer.scoreboard.registerNewObjective("GregChess", "", view.getString("Title"))
        objective.displaySlot = DisplaySlot.SIDEBAR
        val l = gameProperties.size + 1 + playerProperties.size * 2 + 1
        var i = l
        gameProperties.forEach {
            it.team = newTeam()
            it.team?.addEntry(view.getFormatString("GeneralFormat", it.name))
            objective.getScore(view.getFormatString("GeneralFormat", it.name)).score = i--
        }
        objective.getScore(chatColor("&r").repeat(i)).score = i--
        playerProperties.forEach {
            it.teamWhite = newTeam()
            it.teamWhite?.addEntry(view.getFormatString("WhiteFormat", it.name))
            objective.getScore(view.getFormatString("WhiteFormat", it.name)).score = i--
        }
        objective.getScore(chatColor("&r").repeat(i)).score = i--
        playerProperties.forEach {
            it.teamBlack = newTeam()
            it.teamBlack?.addEntry(view.getFormatString("BlackFormat", it.name))
            objective.getScore(view.getFormatString("BlackFormat", it.name)).score = i--
        }
    }

    override fun update() {
        gameProperties.forEach {
            it.team?.suffix = it()
        }
        playerProperties.forEach {
            it.teamWhite?.suffix = it(ChessSide.WHITE)
            it.teamBlack?.suffix = it(ChessSide.BLACK)
        }
    }

    override fun clear() {
        game.renderer.clearScoreboard()
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