package gregc.gregchess.chess.component

import gregc.gregchess.Config
import gregc.gregchess.ConfigPath
import gregc.gregchess.chatColor
import gregc.gregchess.chess.*
import gregc.gregchess.randomString
import org.bukkit.Bukkit
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Team

class ScoreboardManager(private val game: ChessGame): Component {
    class Settings: Component.Settings<ScoreboardManager> {
        override fun getComponent(game: ChessGame) = ScoreboardManager(game)
    }

    private val gameProperties = mutableListOf<GameProperty>()
    private val playerProperties = mutableListOf<PlayerProperty>()

    private val view = Config.component.scoreboard

    val scoreboard = Bukkit.getScoreboardManager()!!.newScoreboard

    private val objective = scoreboard.registerNewObjective("GregChess", "", view.title.get(game.config))

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
            GameProperty(view.preset) {
            override fun invoke() = game.settings.name
        }
        this += object :
            PlayerProperty(view.player) {
            override fun invoke(s: Side) = view.playerPrefix.get(game.config) + game[s].name
        }
    }

    @GameEvent(GameBaseEvent.START, mod = TimeModifier.LATE)
    fun start() {
        game.players.forEachReal { (it as? BukkitPlayer)?.player?.scoreboard = scoreboard }
        objective.displaySlot = DisplaySlot.SIDEBAR
        val l = gameProperties.size + 1 + playerProperties.size * 2 + 1
        var i = l
        gameProperties.forEach {
            it.team = newTeam()
            it.team?.addEntry(view.generalFormat(it.name.get(game.config)).get(game.config))
            objective.getScore(view.generalFormat(it.name.get(game.config)).get(game.config)).score = i--
        }
        objective.getScore(chatColor("&r").repeat(i)).score = i--
        playerProperties.forEach {
            it.teams.white = newTeam()
            it.teams.white?.addEntry(view.whiteFormat(it.name.get(game.config)).get(game.config))
            objective.getScore(view.whiteFormat(it.name.get(game.config)).get(game.config)).score = i--
        }
        objective.getScore(chatColor("&r").repeat(i)).score = i--
        playerProperties.forEach {
            it.teams.black = newTeam()
            it.teams.black?.addEntry(view.blackFormat(it.name.get(game.config)).get(game.config))
            objective.getScore(view.blackFormat(it.name.get(game.config)).get(game.config)).score = i--
        }
    }

    @GameEvent(GameBaseEvent.SPECTATOR_JOIN, GameBaseEvent.RESET_PLAYER, relaxed = true)
    fun spectatorJoin(p: BukkitPlayer) {
        p.player.scoreboard = scoreboard
    }

    @GameEvent(GameBaseEvent.UPDATE, mod = TimeModifier.LATE)
    fun update() {
        gameProperties.forEach {
            it.team?.suffix = it()
        }
        playerProperties.forEach {
            it.teams.forEachIndexed { s, t -> t?.suffix = it(s) }
        }
    }

    @GameEvent(GameBaseEvent.CLEAR, GameBaseEvent.PANIC)
    fun stop() {
        scoreboard.teams.forEach { it.unregister() }
        scoreboard.objectives.forEach { it.unregister() }
    }
}

abstract class PlayerProperty(val name: ConfigPath<String>) {
    val teams: MutableBySides<Team?> = MutableBySides(null)

    abstract operator fun invoke(s: Side): String
}

abstract class GameProperty(val name: ConfigPath<String>) {
    var team: Team? = null

    abstract operator fun invoke(): String
}