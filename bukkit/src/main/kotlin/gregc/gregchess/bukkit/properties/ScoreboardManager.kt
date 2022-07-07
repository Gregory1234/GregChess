package gregc.gregchess.bukkit.properties

import gregc.gregchess.Register
import gregc.gregchess.bukkit.BukkitRegistering
import gregc.gregchess.bukkit.config
import gregc.gregchess.bukkit.match.*
import gregc.gregchess.bukkit.player.forEachReal
import gregc.gregchess.bukkit.registry.BukkitRegistry
import gregc.gregchess.bukkit.registry.getFromRegistry
import gregc.gregchess.bukkit.renderer.ResetPlayerEvent
import gregc.gregchess.bukkitutils.getPathString
import gregc.gregchess.match.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.bukkit.Bukkit
import org.bukkit.entity.Player

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

    @ChessEventHandler
    fun handleEvents(match: ChessMatch, e: ChessBaseEvent) {
        if (e == ChessBaseEvent.UPDATE)
            update()
    }

    @ChessEventHandler
    fun onBaseEvent(match: ChessMatch, e: ChessBaseEvent) {
        if (e == ChessBaseEvent.START || e == ChessBaseEvent.SYNC) start(match)
        else if (e == ChessBaseEvent.STOP) update()
        else if (e == ChessBaseEvent.CLEAR || e == ChessBaseEvent.PANIC) stop()
    }

    @ChessEventHandler
    fun resetPlayer(match: ChessMatch, e: ResetPlayerEvent) {
        giveScoreboard(e.player)
    }

    private fun giveScoreboard(p: Player) {
        p.scoreboard = scoreboard
    }

    private fun start(match: ChessMatch) {
        val playerProperties = mutableMapOf<PropertyType, PlayerProperty>()
        val matchProperties = mutableMapOf<PropertyType, MatchProperty>()

        val e = AddPropertiesEvent(playerProperties, matchProperties)
        e.player(PLAYER) { playerPrefix + match[it].name }
        match.callEvent(e)

        match.sides.forEachReal(::giveScoreboard)
        layout.init(playerProperties, matchProperties)
    }

    @ChessEventHandler
    fun playerEvent(match: ChessMatch, p: PlayerEvent) = when(p.dir) {
        PlayerDirection.JOIN -> giveScoreboard(p.player)
        PlayerDirection.LEAVE -> p.player.scoreboard = Bukkit.getScoreboardManager()!!.mainScoreboard
    }

    @ChessEventHandler
    fun spectatorEvent(match: ChessMatch, p: SpectatorEvent) = when(p.dir) {
        PlayerDirection.JOIN -> giveScoreboard(p.player)
        PlayerDirection.LEAVE -> p.player.scoreboard = Bukkit.getScoreboardManager()!!.mainScoreboard
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