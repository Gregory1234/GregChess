package gregc.gregchess.bukkit.properties

import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.component.BukkitComponentType
import gregc.gregchess.bukkit.config
import gregc.gregchess.bukkit.player.*
import gregc.gregchess.bukkit.renderer.ResetPlayerEvent
import gregc.gregchess.bukkitutils.getPathString
import gregc.gregchess.component.Component
import gregc.gregchess.event.ChessBaseEvent
import gregc.gregchess.event.EventListenerRegistry
import gregc.gregchess.match.ChessMatch
import gregc.gregchess.registry.Register
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

    override fun init(match: ChessMatch, events: EventListenerRegistry) {
        events.register<ChessBaseEvent> {
            when (it) {
                ChessBaseEvent.UPDATE -> update()
                ChessBaseEvent.START, ChessBaseEvent.SYNC -> start(match)
                ChessBaseEvent.STOP -> update()
                ChessBaseEvent.CLEAR, ChessBaseEvent.PANIC -> stop()
                else -> {}
            }
        }
        events.registerR<ResetPlayerEvent> { giveScoreboard(player) }
        events.registerR<PlayerEvent> {
            when(dir) {
                PlayerDirection.JOIN -> giveScoreboard(player)
                PlayerDirection.LEAVE -> player.entity?.scoreboard = Bukkit.getScoreboardManager()!!.mainScoreboard
            }
        }
        events.registerR<SpectatorEvent> {
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