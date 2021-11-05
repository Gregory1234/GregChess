package gregc.gregchess.bukkit.chess.component

import gregc.gregchess.*
import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.chess.*
import gregc.gregchess.bukkit.chess.player.forEachReal
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.SimpleComponent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Team

class ScoreboardManager(game: ChessGame) : SimpleComponent(game) {

    companion object {
        private val TITLE = Message(config, "Scoreboard.Title")
        private fun whiteFormat(s: String) = config.getPathString("Scoreboard.Format.White", s)
        private fun blackFormat(s: String) = config.getPathString("Scoreboard.Format.Black", s)
        private fun generalFormat(s: String) = config.getPathString("Scoreboard.Format.General", s)
        private fun format(color: Color, s: String) = config.getPathString("Scoreboard.Format.${color.configName}", s)

        private val playerPrefix get() = config.getPathString("Scoreboard.PlayerPrefix")

        @JvmField
        val PRESET = GregChessModule.register("preset", PropertyType())

        @JvmField
        val PLAYER = GregChessModule.register("player", PropertyType())
    }

    private val scoreboard = Bukkit.getScoreboardManager()!!.newScoreboard

    private val gameProperties = mutableMapOf<PropertyType, GameProperty>()
    private val playerProperties = mutableMapOf<PropertyType, PlayerProperty>()

    private val gamePropertyTeams = mutableMapOf<PropertyType, Team>()
    private val playerPropertyTeams = mutableMapOf<PropertyType, ByColor<Team>>()

    private val objective = scoreboard.registerNewObjective("GregChess", "", TITLE.get())

    private fun newTeam(): Team {
        var s: String
        do {
            s = randomString(16)
        } while (scoreboard.getTeam(s) != null)
        return scoreboard.registerNewTeam(s)
    }

    @ChessEventHandler
    fun handleEvents(e: GameBaseEvent) {
        if (e == GameBaseEvent.UPDATE)
            update()
    }

    @ChessEventHandler
    fun onStart(e: GameStartStageEvent) {
        if (e == GameStartStageEvent.INIT) init()
        else if (e == GameStartStageEvent.START) start()
    }

    @ChessEventHandler
    fun onStop(e: GameStopStageEvent) {
        if (e == GameStopStageEvent.STOP)
            update()
        if (e == GameStopStageEvent.CLEAR || e == GameStopStageEvent.PANIC)
            stop()
    }

    private fun init() {
        val e = AddPropertiesEvent(playerProperties, gameProperties)
        e.game(PRESET) { game.settings.name }
        e.player(PLAYER) { playerPrefix + game[it].name }
        game.callEvent(e)
    }

    @ChessEventHandler
    fun resetPlayer(e: ResetPlayerEvent) {
        giveScoreboard(e.player)
    }

    private fun giveScoreboard(p: Player) {
        p.scoreboard = scoreboard
    }

    private fun start() {
        game.players.forEachReal(::giveScoreboard)
        objective.displaySlot = DisplaySlot.SIDEBAR
        val l = gameProperties.size + 1 + playerProperties.size * 2 + 1
        var i = l
        for (t in gameProperties.keys) {
            gamePropertyTeams[t] = newTeam().apply {
                addEntry(generalFormat(t.localName))
            }
            objective.getScore(generalFormat(t.localName)).score = i--
        }
        for (t in playerProperties.keys) {
            playerPropertyTeams[t] = byColor { s ->
                newTeam().apply { addEntry(format(s, t.localName)) }
            }
        }
        objective.getScore("&r".chatColor().repeat(i)).score = i--
        for (t in playerProperties.keys) {
            objective.getScore(whiteFormat(t.localName)).score = i--
        }
        objective.getScore("&r".chatColor().repeat(i)).score = i--
        for (t in playerProperties.keys) {
            objective.getScore(blackFormat(t.localName)).score = i--
        }
    }

    @ChessEventHandler
    fun spectatorJoin(p: SpectatorEvent) {
        if (p.dir == PlayerDirection.JOIN)
            giveScoreboard(p.player)
    }

    private fun update() = with(MultiExceptionContext()) {
        for ((t, v) in gamePropertyTeams)
            v.suffix = exec("ERROR", { PropertyException(t, null, it) }) { gameProperties[t]!!().chatColor() }

        for ((tp, v) in playerPropertyTeams) {
            for ((s, t) in v.toIndexedList())
                t.suffix = exec("ERROR", { PropertyException(tp, s, it) }) { playerProperties[tp]!!(s).chatColor() }
        }
        rethrow()
    }

    private fun stop() {
        for (it in scoreboard.teams) {
            it.unregister()
        }
        for (it in scoreboard.objectives) {
            it.unregister()
        }
    }
}