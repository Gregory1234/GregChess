package gregc.gregchess.chess.component

import gregc.gregchess.*
import gregc.gregchess.chess.*
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Team

class ScoreboardManager(private val game: ChessGame, private val settings: Settings): Component {
    class Settings {
        fun getComponent(game: ChessGame) = ScoreboardManager(game, this)
    }

    private val gameProperties = mutableListOf<GameProperty>()
    private val playerProperties = mutableListOf<PlayerProperty>()

    private val view = ConfigManager.getView("Component.Scoreboard")

    val scoreboard = Bukkit.getScoreboardManager()!!.newScoreboard

    private val objective = scoreboard.registerNewObjective("GregChess", "", view.getString("Title"))

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
        } while (scoreboard.getTeam(s) != null)
        return scoreboard.registerNewTeam(s)
    }

    @GameEvent(GameBaseEvent.INIT)
    fun init() {
        this += object :
            GameProperty(ConfigManager.getString("Component.Scoreboard.Preset")) {
            override fun invoke() = game.settings.name
        }
        this += object :
            PlayerProperty(ConfigManager.getString("Component.Scoreboard.Player")) {
            override fun invoke(s: ChessSide) =
                ConfigManager.getString("Component.Scoreboard.PlayerPrefix") + game[s].name
        }
    }

    @GameEvent(GameBaseEvent.START, TimeModifier.LATE)
    fun start() {
        game.forEachPlayer { it.scoreboard = scoreboard }
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

    @GameEvent(GameBaseEvent.SPECTATOR_JOIN)
    fun spectatorJoin(p: Player) {
        p.scoreboard = scoreboard
    }

    @GameEvent(GameBaseEvent.UPDATE, TimeModifier.LATE)
    fun update() {
        gameProperties.forEach {
            it.team?.suffix = it()
        }
        playerProperties.forEach {
            it.teamWhite?.suffix = it(ChessSide.WHITE)
            it.teamBlack?.suffix = it(ChessSide.BLACK)
        }
    }

    @GameEvent(GameBaseEvent.CLEAR)
    fun stop() {
        scoreboard.teams.forEach { it.unregister() }
        scoreboard.objectives.forEach { it.unregister() }
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