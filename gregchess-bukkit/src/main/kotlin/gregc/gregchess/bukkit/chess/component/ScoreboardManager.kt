package gregc.gregchess.bukkit.chess.component

import gregc.gregchess.MultiExceptionContext
import gregc.gregchess.bukkit.chess.*
import gregc.gregchess.bukkit.chess.player.forEachRealBukkit
import gregc.gregchess.bukkit.config
import gregc.gregchess.bukkitutils.*
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Component
import gregc.gregchess.registry.Register
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Team

@Serializable
class ScoreboardManager : Component {

    companion object {
        private val TITLE = Message(config, "Scoreboard.Title")
        private fun whiteFormat(s: String) = config.getPathString("Scoreboard.Format.White", s)
        private fun blackFormat(s: String) = config.getPathString("Scoreboard.Format.Black", s)
        private fun generalFormat(s: String) = config.getPathString("Scoreboard.Format.General", s)
        private fun format(color: Color, s: String) = config.getPathString("Scoreboard.Format.${color.configName}", s)

        private val playerPrefix get() = config.getPathString("Scoreboard.PlayerPrefix")

        @JvmField
        @Register
        val PRESET = PropertyType()

        @JvmField
        @Register
        val PLAYER = PropertyType()
    }

    @Transient
    private val scoreboard = Bukkit.getScoreboardManager()!!.newScoreboard

    @Transient
    private val gameProperties = mutableMapOf<PropertyType, GameProperty>()
    @Transient
    private val playerProperties = mutableMapOf<PropertyType, PlayerProperty>()

    @Transient
    private val gamePropertyTeams = mutableMapOf<PropertyType, Team>()
    @Transient
    private val playerPropertyTeams = mutableMapOf<PropertyType, ByColor<Team>>()

    @Transient
    private val objective = scoreboard.registerNewObjective("GregChess", "", TITLE.get())

    private fun randomString(size: Int) =
        String(CharArray(size) { (('a'..'z') + ('A'..'Z') + ('0'..'9')).random() })

    private fun newTeam(): Team {
        var s: String
        do {
            s = randomString(16)
        } while (scoreboard.getTeam(s) != null)
        return scoreboard.registerNewTeam(s)
    }

    @ChessEventHandler
    fun handleEvents(game: ChessGame, e: GameBaseEvent) {
        if (e == GameBaseEvent.UPDATE)
            update()
    }

    @ChessEventHandler
    fun onStart(game: ChessGame, e: GameStartStageEvent) {
        if (e == GameStartStageEvent.INIT) init(game)
        else if (e == GameStartStageEvent.START) start(game)
    }

    @ChessEventHandler
    fun onStop(game: ChessGame, e: GameStopStageEvent) {
        if (e == GameStopStageEvent.STOP)
            update()
        if (e == GameStopStageEvent.CLEAR || e == GameStopStageEvent.PANIC)
            stop()
    }

    private fun init(game: ChessGame) {
        val e = AddPropertiesEvent(playerProperties, gameProperties)
        e.game(PRESET) { game.settings.name }
        e.player(PLAYER) { playerPrefix + game[it].name }
        game.callEvent(e)
    }

    @ChessEventHandler
    fun resetPlayer(game: ChessGame, e: ResetPlayerEvent) {
        giveScoreboard(e.player)
    }

    private fun giveScoreboard(p: Player) {
        p.scoreboard = scoreboard
    }

    private fun start(game: ChessGame) {
        game.sides.forEachRealBukkit(::giveScoreboard)
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
    fun spectatorJoin(game: ChessGame, p: SpectatorEvent) {
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