package gregc.gregchess.bukkit.properties

import gregc.gregchess.Register
import gregc.gregchess.bukkit.BukkitRegistering
import gregc.gregchess.bukkit.component.BukkitComponentType
import gregc.gregchess.bukkit.config
import gregc.gregchess.bukkit.event.BukkitChessEventType
import gregc.gregchess.bukkit.event.PlayerDirection
import gregc.gregchess.bukkit.player.BukkitPlayer
import gregc.gregchess.bukkit.player.forEachReal
import gregc.gregchess.bukkit.registry.BukkitRegistry
import gregc.gregchess.bukkit.registry.getFromRegistry
import gregc.gregchess.bukkitutils.getPathString
import gregc.gregchess.component.Component
import gregc.gregchess.event.*
import gregc.gregchess.match.ChessMatch
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.bukkit.Bukkit

// TODO: make it removable
@Serializable
class ScoreboardManager : Component {

    companion object : BukkitRegistering {

        private val playerPrefix get() = config.getPathString("Scoreboard.PlayerPrefix")

        @JvmField
        @Register
        val PLAYER = PropertyType()
    }

    override val type get() = BukkitComponentType.SCOREBOARD_MANAGER

    @Transient
    private val scoreboard = Bukkit.getScoreboardManager()!!.newScoreboard

    @Transient
    private val layout: ScoreboardLayout = config.getFromRegistry(BukkitRegistry.SCOREBOARD_LAYOUT_PROVIDER, "Scoreboard.Layout")!!(scoreboard)

    override fun init(match: ChessMatch, events: ChessEventRegistry) {
        events.register(ChessEventType.BASE) {
            when (it) {
                ChessBaseEvent.UPDATE -> update()
                ChessBaseEvent.START, ChessBaseEvent.SYNC -> start(match)
                ChessBaseEvent.STOP -> update()
                ChessBaseEvent.CLEAR, ChessBaseEvent.PANIC -> stop()
                else -> {}
            }
        }
        events.registerR(BukkitChessEventType.RESET_PLAYER) { giveScoreboard(player) }
        events.registerR(BukkitChessEventType.PLAYER) {
            when(dir) {
                PlayerDirection.JOIN -> giveScoreboard(player)
                PlayerDirection.LEAVE -> player.entity?.scoreboard = Bukkit.getScoreboardManager()!!.mainScoreboard
            }
        }
        events.registerR(BukkitChessEventType.SPECTATOR) {
            when(dir) {
                PlayerDirection.JOIN -> giveScoreboard(player)
                PlayerDirection.LEAVE -> player.entity?.scoreboard = Bukkit.getScoreboardManager()!!.mainScoreboard
            }
        }
    }

    private fun giveScoreboard(p: BukkitPlayer) {
        p.entity?.scoreboard = scoreboard
    }

    private fun start(match: ChessMatch) {
        val playerProperties = mutableMapOf<PropertyType, PlayerProperty>()
        val matchProperties = mutableMapOf<PropertyType, MatchProperty>()

        val e = AddPropertiesEvent(playerProperties, matchProperties)
        e.player(PLAYER) { playerPrefix + match.sides[it].name }
        match.callEvent(e)

        match.sides.forEachReal(::giveScoreboard)
        layout.init(playerProperties, matchProperties)
    }

    private fun update() = layout.update()

    private fun stop() {
        for (it in scoreboard.teams) {
            it.unregister()
        }
        for (it in scoreboard.objectives) {
            it.unregister()
        }
    }
}