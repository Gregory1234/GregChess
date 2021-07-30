package gregc.gregchess.bukkit.chess.component

import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.chess.*
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.*
import gregc.gregchess.randomString
import org.bukkit.Bukkit
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Team

class ScoreboardManager(private val game: ChessGame): Component {
    object Settings : Component.Settings<ScoreboardManager> {
        override fun getComponent(game: ChessGame) = ScoreboardManager(game)
    }

    companion object {
        private val TITLE = config.getLocalizedString("Scoreboard.Title")
        private fun whiteFormat(s: String) = config.getLocalizedString("Scoreboard.Format.White", s)
        private fun blackFormat(s: String) = config.getLocalizedString("Scoreboard.Format.Black", s)
        private fun generalFormat(s: String) = config.getLocalizedString("Scoreboard.Format.General", s)
        private fun format(side: Side, s: String) = config.getLocalizedString("Scoreboard.Format.${side.configName}", s)

        private val playerPrefix get() = config.getString("Scoreboard.PlayerPrefix")!!

        @JvmField
        val PRESET = PropertyType<String>("PRESET")
        @JvmField
        val PLAYER = PropertyType<String>("PLAYER")
    }

    private val scoreboard = Bukkit.getScoreboardManager()!!.newScoreboard

    private val gameProperties = mutableMapOf<PropertyType<*>, GameProperty<*>>()
    private val playerProperties = mutableMapOf<PropertyType<*>, PlayerProperty<*>>()

    private val gamePropertyTeams = mutableMapOf<PropertyType<*>, Team>()
    private val playerPropertyTeams = mutableMapOf<PropertyType<*>, BySides<Team>>()

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
        GameBaseEvent.UPDATE -> update()
        else -> {}
    }

    @ChessEventHandler
    fun onStart(e: GameStartStageEvent) = when(e) {
        GameStartStageEvent.INIT -> init()
        GameStartStageEvent.START -> start()
        else -> {}
    }

    @ChessEventHandler
    fun onStop(e: GameStopStageEvent) = when(e) {
        GameStopStageEvent.CLEAR, GameStopStageEvent.PANIC -> stop()
        else -> {}
    }

    private fun init() {
        val e = AddPropertiesEvent(playerProperties, gameProperties)
        e.game(PRESET) { game.settings.name }
        e.player(PLAYER) { playerPrefix + game[it].name }
        game.callEvent(e)
    }

    private fun start() {
        game.players.forEachReal { (it as? BukkitPlayer)?.player?.scoreboard = scoreboard }
        objective.displaySlot = DisplaySlot.SIDEBAR
        val l = gameProperties.size + 1 + playerProperties.size * 2 + 1
        var i = l
        gameProperties.values.forEach {
            gamePropertyTeams[it.type] = newTeam().apply {
                addEntry(generalFormat(it.type.localName).get(DEFAULT_LANG).chatColor())
            }
            objective.getScore(generalFormat(it.type.localName).get(DEFAULT_LANG).chatColor()).score = i--
        }
        playerProperties.values.forEach {
            playerPropertyTeams[it.type] = BySides { s ->
                newTeam().apply { addEntry(format(s, it.type.localName).get(DEFAULT_LANG).chatColor()) }
            }
        }
        objective.getScore("&r".chatColor().repeat(i)).score = i--
        playerProperties.values.forEach {
            objective.getScore(whiteFormat(it.type.localName).get(DEFAULT_LANG).chatColor()).score = i--
        }
        objective.getScore("&r".chatColor().repeat(i)).score = i--
        playerProperties.values.forEach {
            objective.getScore(blackFormat(it.type.localName).get(DEFAULT_LANG).chatColor()).score = i--
        }
    }

    @ChessEventHandler
    fun spectatorJoin(p: SpectatorEvent) {
        if (p.dir == PlayerDirection.JOIN)
            p.human.bukkit.scoreboard = scoreboard
    }

    private fun update() {
        gameProperties.values.forEach {
            gamePropertyTeams[it.type]?.suffix = it.asString().chatColor()
        }
        playerProperties.values.forEach {
            playerPropertyTeams[it.type]?.forEachIndexed { s, t -> t.suffix = it.asString(s).chatColor() }
        }
    }

    private fun stop() {
        scoreboard.teams.forEach { it.unregister() }
        scoreboard.objectives.forEach { it.unregister() }
    }
}