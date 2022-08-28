package gregc.gregchess.bukkit.properties

import gregc.gregchess.Register
import gregc.gregchess.bukkit.BukkitRegistering
import gregc.gregchess.bukkit.config
import gregc.gregchess.bukkit.match.*
import gregc.gregchess.bukkit.player.BukkitPlayer
import gregc.gregchess.bukkit.player.forEachReal
import gregc.gregchess.bukkit.registry.BukkitRegistry
import gregc.gregchess.bukkit.registry.getFromRegistry
import gregc.gregchess.bukkitutils.getPathString
import gregc.gregchess.match.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.bukkit.Bukkit

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

    override fun init(match: ChessMatch, eventManager: ChessEventManager) {
        eventManager.registerEvent(ChessEventType.BASE) {
            when (it) {
                ChessBaseEvent.UPDATE -> update()
                ChessBaseEvent.START, ChessBaseEvent.SYNC -> start(match)
                ChessBaseEvent.STOP -> update()
                ChessBaseEvent.CLEAR, ChessBaseEvent.PANIC -> stop()
                else -> {}
            }
        }
        eventManager.registerEventR(BukkitChessEventType.RESET_PLAYER) { giveScoreboard(player) }
        eventManager.registerEventR(BukkitChessEventType.PLAYER) {
            when(dir) {
                PlayerDirection.JOIN -> giveScoreboard(player)
                PlayerDirection.LEAVE -> player.entity?.scoreboard = Bukkit.getScoreboardManager()!!.mainScoreboard
            }
        }
        eventManager.registerEventR(BukkitChessEventType.SPECTATOR) {
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
        e.player(PLAYER) { playerPrefix + match[it].name }
        match.callEvent(e)

        match.sideFacades.forEachReal(::giveScoreboard)
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