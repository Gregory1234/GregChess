package gregc.gregchess.chess.component

import gregc.gregchess.*
import gregc.gregchess.chess.*
import org.bukkit.Bukkit
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Team

class BukkitScoreboardManager(private val game: ChessGame) : ScoreboardManager {
    object Settings : Component.Settings<BukkitScoreboardManager> {
        override fun getComponent(game: ChessGame) = BukkitScoreboardManager(game)
    }

    companion object {
        private val TITLE = LocalizedString(config, "Scoreboard.Title")
        private fun whiteFormat(s: String) = LocalizedString(config, "Scoreboard.Format.White", s)
        private fun blackFormat(s: String) = LocalizedString(config, "Scoreboard.Format.Black", s)
        private fun generalFormat(s: String) = LocalizedString(config, "Scoreboard.Format.General", s)
        private fun format(side: Side, s: String) = LocalizedString(config, "Scoreboard.Format.${side.standardName}", s)

        private val playerPrefix get() = config.getString("Scoreboard.PlayerPrefix")

        private val GameProperty.name get() = LocalizedString(config, "Scoreboard.$standardName").get(DEFAULT_LANG)
        private val PlayerProperty.name get() = LocalizedString(config, "Scoreboard.$standardName").get(DEFAULT_LANG)
    }

    private val gameProperties = mutableListOf<GameProperty>()
    private val playerProperties = mutableListOf<PlayerProperty>()

    private val scoreboard = Bukkit.getScoreboardManager()!!.newScoreboard

    private val gamePropertyTeams = mutableMapOf<GameProperty, Team>()
    private val playerPropertyTeams = mutableMapOf<PlayerProperty, BySides<Team>>()

    private val objective = scoreboard.registerNewObjective("GregChess", "", TITLE.get(DEFAULT_LANG))

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
        game("Preset") { game.settings.name }
        player("Player") { playerPrefix + game[it].name }
    }

    @GameEvent(GameBaseEvent.START, mod = TimeModifier.LATE)
    fun start() {
        game.players.forEachReal { (it as? BukkitPlayer)?.player?.scoreboard = scoreboard }
        objective.displaySlot = DisplaySlot.SIDEBAR
        val l = gameProperties.size + 1 + playerProperties.size * 2 + 1
        var i = l
        gameProperties.forEach {
            gamePropertyTeams[it] = newTeam().apply {
                addEntry(generalFormat(it.name).get(DEFAULT_LANG))
            }
            objective.getScore(generalFormat(it.name).get(DEFAULT_LANG)).score = i--
        }
        playerProperties.forEach {
            playerPropertyTeams[it] = BySides { s ->
                newTeam().apply { addEntry(format(s, it.name).get(DEFAULT_LANG)) }
            }
        }
        objective.getScore(chatColor("&r").repeat(i)).score = i--
        playerProperties.forEach {
            objective.getScore(whiteFormat(it.name).get(DEFAULT_LANG)).score = i--
        }
        objective.getScore(chatColor("&r").repeat(i)).score = i--
        playerProperties.forEach {
            objective.getScore(blackFormat(it.name).get(DEFAULT_LANG)).score = i--
        }
    }

    @ChessEventHandler
    fun spectatorJoin(p: SpectatorJoinEvent) {
        p.player.player.scoreboard = scoreboard
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