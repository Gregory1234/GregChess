package gregc.gregchess.bukkit.match

import gregc.gregchess.bukkit.player.showMatchResults
import gregc.gregchess.match.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.bukkit.entity.Player

data class SpectatorEvent(val player: Player, val dir: PlayerDirection) : ChessEvent

class SpectatorNotFoundException(player: Player) : Exception(player.name)

@Serializable
class SpectatorManager : Component { // TODO: consider reworking the spectator system

    override val type get() = BukkitComponentType.SPECTATOR_MANAGER

    @Transient
    private lateinit var match: ChessMatch

    override fun init(match: ChessMatch) {
        this.match = match
    }

    @Transient
    private val spectatorList = mutableListOf<Player>()

    val spectators get() = spectatorList.toList()

    operator fun plusAssign(p: Player) {
        ChessMatchManager.setCurrentSpectatedMatch(p.uniqueId, match.uuid) // TODO: this shouldn't be called here probably
        spectatorList += p
        match.callEvent(SpectatorEvent(p, PlayerDirection.JOIN))
    }

    operator fun minusAssign(p: Player) {
        if (p !in spectatorList)
            throw SpectatorNotFoundException(p)
        ChessMatchManager.setCurrentSpectatedMatch(p.uniqueId, null)
        spectatorList -= p
        match.callEvent(SpectatorEvent(p, PlayerDirection.LEAVE))
    }

    @ChessEventHandler
    fun onStop(e: ChessBaseEvent) {
        if (e == ChessBaseEvent.STOP) stop()
        else if (e == ChessBaseEvent.CLEAR) clear()
    }

    private fun stop() {
        for (it in spectators) {
            it.showMatchResults(match.results!!)
        }
    }

    private fun clear() {
        val s = spectators
        spectatorList.clear()
        for (it in s) {
            match.callEvent(SpectatorEvent(it, PlayerDirection.LEAVE))
        }
    }
}

val ChessMatch.spectators get() = require(BukkitComponentType.SPECTATOR_MANAGER)