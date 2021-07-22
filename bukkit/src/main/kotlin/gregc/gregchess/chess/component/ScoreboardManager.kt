package gregc.gregchess.chess.component

import gregc.gregchess.*
import gregc.gregchess.chess.*
import org.bukkit.Bukkit
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Team
import java.time.Duration

class ScoreboardManager(private val game: ChessGame): Component {
    object Settings : Component.Settings<ScoreboardManager> {
        override fun getComponent(game: ChessGame) = ScoreboardManager(game)
    }

    companion object {
        private val TITLE = config.getLocalizedString("Scoreboard.Title")
        private fun whiteFormat(s: String) = config.getLocalizedString("Scoreboard.Format.White", s)
        private fun blackFormat(s: String) = config.getLocalizedString("Scoreboard.Format.Black", s)
        private fun generalFormat(s: String) = config.getLocalizedString("Scoreboard.Format.General", s)
        private fun format(side: Side, s: String) = config.getLocalizedString("Scoreboard.Format.${side.standardName}", s)

        private val playerPrefix get() = config.getString("Scoreboard.PlayerPrefix")!!

        private val GameProperty<*>.name get() = config.getLocalizedString("Scoreboard.${id.path.snakeToPascal()}").get(DEFAULT_LANG)
        private val PlayerProperty<*>.name get() = config.getLocalizedString("Scoreboard.${id.path.snakeToPascal()}").get(DEFAULT_LANG)

        private val timeFormat: String get() = config.getString("TimeFormat")!!

        private fun <T> T.stringify(): String {
            if (this is Duration)
                return format(timeFormat) ?: timeFormat
            return toString()
        }
    }

    private val scoreboard = Bukkit.getScoreboardManager()!!.newScoreboard

    private val gameProperties = mutableMapOf<Identifier, GameProperty<*>>()
    private val playerProperties = mutableMapOf<Identifier, PlayerProperty<*>>()

    private val gamePropertyTeams = mutableMapOf<Identifier, Team>()
    private val playerPropertyTeams = mutableMapOf<Identifier, BySides<Team>>()

    private val objective = scoreboard.registerNewObjective("GregChess", "", TITLE.get(DEFAULT_LANG))

    private fun newTeam(): Team {
        var s: String
        do {
            s = randomString(16)
        } while (scoreboard.getTeam(s) != null)
        return scoreboard.registerNewTeam(s)
    }

    @ChessEventHandler
    fun handleEvents(e: GameBaseEvent) = when (e) {
        GameBaseEvent.INIT -> init()
        GameBaseEvent.START -> start()
        GameBaseEvent.CLEAR, GameBaseEvent.PANIC -> stop()
        GameBaseEvent.UPDATE -> update()
        else -> {}
    }

    private fun init() {
        val e = AddPropertiesEvent(playerProperties, gameProperties)
        e.game("preset".asIdent()) { game.settings.name }
        e.player("player".asIdent()) { playerPrefix + game[it].name }
        game.components.callEvent(e)
    }

    private fun start() {
        game.players.forEachReal { (it as? BukkitPlayer)?.player?.scoreboard = scoreboard }
        objective.displaySlot = DisplaySlot.SIDEBAR
        val l = gameProperties.size + 1 + playerProperties.size * 2 + 1
        var i = l
        gameProperties.values.forEach {
            gamePropertyTeams[it.id] = newTeam().apply {
                addEntry(generalFormat(it.name).get(DEFAULT_LANG).chatColor())
            }
            objective.getScore(generalFormat(it.name).get(DEFAULT_LANG).chatColor()).score = i--
        }
        playerProperties.values.forEach {
            playerPropertyTeams[it.id] = BySides { s ->
                newTeam().apply { addEntry(format(s, it.name).get(DEFAULT_LANG).chatColor()) }
            }
        }
        objective.getScore("&r".chatColor().repeat(i)).score = i--
        playerProperties.values.forEach {
            objective.getScore(whiteFormat(it.name).get(DEFAULT_LANG).chatColor()).score = i--
        }
        objective.getScore("&r".chatColor().repeat(i)).score = i--
        playerProperties.values.forEach {
            objective.getScore(blackFormat(it.name).get(DEFAULT_LANG).chatColor()).score = i--
        }
    }

    @ChessEventHandler
    fun spectatorJoin(p: SpectatorEvent) {
        if (p.dir == PlayerDirection.JOIN)
            p.human.bukkit.scoreboard = scoreboard
    }

    private fun update() {
        gameProperties.values.forEach {
            gamePropertyTeams[it.id]?.suffix = it().stringify().chatColor()
        }
        playerProperties.values.forEach {
            playerPropertyTeams[it.id]?.forEachIndexed { s, t -> t.suffix = it(s).stringify().chatColor() }
        }
    }

    private fun stop() {
        scoreboard.teams.forEach { it.unregister() }
        scoreboard.objectives.forEach { it.unregister() }
    }
}