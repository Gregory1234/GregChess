package gregc.gregchess.bukkit.properties

import gregc.gregchess.*
import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.game.*
import gregc.gregchess.bukkit.player.forEachReal
import gregc.gregchess.bukkit.renderer.ResetPlayerEvent
import gregc.gregchess.bukkitutils.*
import gregc.gregchess.game.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Team

// TODO: add a way to use a custom scoreboard layout
@Serializable
class ScoreboardManager : Component {

    companion object : BukkitRegistering {
        private val TITLE = Message(config, "Scoreboard.Title")
        private fun whiteFormat(s: String) = config.getPathString("Scoreboard.Format.White", s)
        private fun blackFormat(s: String) = config.getPathString("Scoreboard.Format.Black", s)
        private fun generalFormat(s: String) = config.getPathString("Scoreboard.Format.General", s)
        private fun format(color: Color, s: String) = config.getPathString("Scoreboard.Format.${color.configName}", s)

        private val playerPrefix get() = config.getPathString("Scoreboard.PlayerPrefix")

        @JvmField
        @Register
        val PLAYER = PropertyType()
    }

    override val type get() = BukkitComponentType.SCOREBOARD_MANAGER

    @Transient
    private lateinit var game: ChessGame

    override fun init(game: ChessGame) {
        this.game = game
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
    fun handleEvents(e: GameBaseEvent) {
        if (e == GameBaseEvent.UPDATE)
            update()
    }

    @ChessEventHandler
    fun onBaseEvent(e: GameBaseEvent) {
        if (e == GameBaseEvent.START || e == GameBaseEvent.SYNC) start()
        else if (e == GameBaseEvent.STOP) update()
        else if (e == GameBaseEvent.CLEAR || e == GameBaseEvent.PANIC) stop()
    }

    @ChessEventHandler
    fun resetPlayer(e: ResetPlayerEvent) {
        giveScoreboard(e.player)
    }

    private fun giveScoreboard(p: Player) {
        p.scoreboard = scoreboard
    }

    private fun start() {
        val e = AddPropertiesEvent(playerProperties, gameProperties)
        e.player(PLAYER) { playerPrefix + game[it].name }
        game.callEvent(e)

        game.sides.forEachReal(::giveScoreboard)
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
    fun playerEvent(p: PlayerEvent) = when(p.dir) {
        PlayerDirection.JOIN -> giveScoreboard(p.player)
        PlayerDirection.LEAVE -> p.player.scoreboard = Bukkit.getScoreboardManager()!!.mainScoreboard
    }

    @ChessEventHandler
    fun spectatorEvent(p: SpectatorEvent) = when(p.dir) {
        PlayerDirection.JOIN -> giveScoreboard(p.player)
        PlayerDirection.LEAVE -> p.player.scoreboard = Bukkit.getScoreboardManager()!!.mainScoreboard
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