package gregc.gregchess.bukkit.properties

import gregc.gregchess.*
import gregc.gregchess.bukkit.config
import gregc.gregchess.bukkit.configName
import gregc.gregchess.bukkitutils.*
import gregc.gregchess.utils.MultiExceptionContext
import org.bukkit.scoreboard.*

interface ScoreboardLayout {
    fun init(playerProperties: MutableMap<PropertyType, PlayerProperty>, matchProperties: MutableMap<PropertyType, MatchProperty>)
    fun update()
}

class SimpleScoreboardLayout(private val scoreboard: Scoreboard) : ScoreboardLayout {
    companion object {
        private val TITLE = Message(config, "Scoreboard.Title")
        private fun whiteFormat(s: String) = config.getPathString("Scoreboard.Format.White", s)
        private fun blackFormat(s: String) = config.getPathString("Scoreboard.Format.Black", s)
        private fun generalFormat(s: String) = config.getPathString("Scoreboard.Format.General", s)
        private fun format(color: Color, s: String) = config.getPathString("Scoreboard.Format.${color.configName}", s)
    }

    private val objective = scoreboard.registerNewObjective("GregChess", Criteria.DUMMY, TITLE.get())

    private val playerProperties = mutableMapOf<PropertyType, PlayerProperty>()
    private val matchProperties = mutableMapOf<PropertyType, MatchProperty>()

    private val playerPropertyTeams = mutableMapOf<PropertyType, ByColor<Team>>()
    private val matchPropertyTeams = mutableMapOf<PropertyType, Team>()

    private fun randomString(size: Int) =
        String(CharArray(size) { (('a'..'z') + ('A'..'Z') + ('0'..'9')).random() })

    private fun newTeam(): Team {
        var s: String
        do {
            s = randomString(16)
        } while (scoreboard.getTeam(s) != null)
        return scoreboard.registerNewTeam(s)
    }

    override fun init(
        playerProperties: MutableMap<PropertyType, PlayerProperty>,
        matchProperties: MutableMap<PropertyType, MatchProperty>
    ) {
        this.playerProperties.clear()
        this.matchProperties.clear()
        this.playerProperties.putAll(playerProperties)
        this.matchProperties.putAll(matchProperties)

        objective.displaySlot = DisplaySlot.SIDEBAR
        val l = matchProperties.size + 1 + playerProperties.size * 2 + 1
        var i = l
        for (t in matchProperties.keys) {
            matchPropertyTeams[t] = newTeam().apply {
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

    override fun update() = with(MultiExceptionContext()) {
        for ((t, v) in matchPropertyTeams)
            v.suffix = exec("ERROR", { PropertyException(t, null, it) }) { matchProperties[t]!!().chatColor() }

        for ((tp, v) in playerPropertyTeams) {
            for ((s, t) in v.toIndexedList())
                t.suffix = exec("ERROR", { PropertyException(tp, s, it) }) { playerProperties[tp]!!(s).chatColor() }
        }
        rethrow()
    }
}