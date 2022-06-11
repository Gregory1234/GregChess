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
    private lateinit var match: ChessMatch

    override fun init(match: ChessMatch) {
        this.match = match
    }

    @Transient
    private val scoreboard = Bukkit.getScoreboardManager()!!.newScoreboard

    @Transient
    private val layout: ScoreboardLayout = config.getFromRegistry(BukkitRegistry.SCOREBOARD_LAYOUT_PROVIDER, "Scoreboard.Layout")!!(scoreboard)

    @ChessEventHandler
    fun handleEvents(e: ChessBaseEvent) {
        if (e == ChessBaseEvent.UPDATE)
            update()
    }

    @ChessEventHandler
    fun onBaseEvent(e: ChessBaseEvent) {
        if (e == ChessBaseEvent.START || e == ChessBaseEvent.SYNC) start()
        else if (e == ChessBaseEvent.STOP) update()
        else if (e == ChessBaseEvent.CLEAR || e == ChessBaseEvent.PANIC) stop()
    }

    @ChessEventHandler
    fun resetPlayer(e: ResetPlayerEvent) {
        giveScoreboard(e.player)
    }

    private fun giveScoreboard(p: Player) {
        p.scoreboard = scoreboard
    }

    private fun start() {
        val playerProperties = mutableMapOf<PropertyType, PlayerProperty>()
        val matchProperties = mutableMapOf<PropertyType, MatchProperty>()

        val e = AddPropertiesEvent(playerProperties, matchProperties)
        e.player(PLAYER) { playerPrefix + match[it].name }
        match.callEvent(e)

        match.sides.forEachReal(::giveScoreboard)
        layout.init(playerProperties, matchProperties)
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