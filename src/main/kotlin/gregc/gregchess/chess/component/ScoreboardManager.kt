package gregc.gregchess.chess.component

import gregc.gregchess.Config
import gregc.gregchess.ConfigPath
import gregc.gregchess.chatColor
import gregc.gregchess.chess.*
import gregc.gregchess.randomString
import org.bukkit.Bukkit
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Team

interface ScoreboardManager: Component {
    operator fun plusAssign(p: GameProperty)
    operator fun plusAssign(p: PlayerProperty)
}

class BukkitScoreboardManager(private val game: ChessGame): ScoreboardManager {
    class Settings: Component.Settings<BukkitScoreboardManager> {
        override fun getComponent(game: ChessGame) = BukkitScoreboardManager(game)
    }

    private val gameProperties = mutableListOf<GameProperty>()
    private val playerProperties = mutableListOf<PlayerProperty>()

    private val view = Config.component.scoreboard

    private val scoreboard = Bukkit.getScoreboardManager()!!.newScoreboard

    private val gamePropertyTeams = mutableMapOf<GameProperty, Team>()
    private val playerPropertyTeams = mutableMapOf<PlayerProperty, BySides<Team>>()

    private val objective = scoreboard.registerNewObjective("GregChess", "", view.title.get(game.config))

    override operator fun plusAssign(p: GameProperty) {
        gameProperties += p
    }

    override operator fun plusAssign(p: PlayerProperty) {
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
            gamePropertyTeams[it] = newTeam().apply {
                addEntry(view.format.general(it.name.get(game.config)).get(game.config))
            }
            objective.getScore(view.format.general(it.name.get(game.config)).get(game.config)).score = i--
        }
        playerProperties.forEach {
            playerPropertyTeams[it] = BySides { s ->
                newTeam().apply { addEntry(view.format[s](it.name.get(game.config)).get(game.config)) }
            }
        }
        objective.getScore(chatColor("&r").repeat(i)).score = i--
        playerProperties.forEach {
            objective.getScore(view.format.white(it.name.get(game.config)).get(game.config)).score = i--
        }
        objective.getScore(chatColor("&r").repeat(i)).score = i--
        playerProperties.forEach {
            objective.getScore(view.format.black(it.name.get(game.config)).get(game.config)).score = i--
        }
    }

    @GameEvent(GameBaseEvent.SPECTATOR_JOIN, GameBaseEvent.RESET_PLAYER, relaxed = true)
    fun spectatorJoin(p: BukkitPlayer) {
        p.player.scoreboard = scoreboard
    }

    @GameEvent(GameBaseEvent.UPDATE, mod = TimeModifier.LATE)
    fun update() {
        gameProperties.forEach {
            gamePropertyTeams[it]?.suffix = it()
        }
        playerProperties.forEach {
            playerPropertyTeams[it]?.forEachIndexed { s, t -> t.suffix = it(s) }
        }
    }

    @GameEvent(GameBaseEvent.CLEAR, GameBaseEvent.PANIC)
    fun stop() {
        scoreboard.teams.forEach { it.unregister() }
        scoreboard.objectives.forEach { it.unregister() }
    }
}

abstract class PlayerProperty(val name: ConfigPath<String>) {
    abstract operator fun invoke(s: Side): String
}

abstract class GameProperty(val name: ConfigPath<String>) {
    abstract operator fun invoke(): String
}