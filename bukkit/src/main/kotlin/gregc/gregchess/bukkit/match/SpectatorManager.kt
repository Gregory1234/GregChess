package gregc.gregchess.bukkit.match

import gregc.gregchess.bukkit.player.BukkitPlayer
import gregc.gregchess.bukkit.results.sendMatchResults
import gregc.gregchess.match.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

data class SpectatorEvent(val player: BukkitPlayer, val dir: PlayerDirection) : ChessEvent {
    override val type get() = BukkitChessEventType.SPECTATOR
}

@Serializable
class SpectatorManager : Component {

    override val type get() = BukkitComponentType.SPECTATOR_MANAGER

    @Transient
    private val spectatorList = mutableListOf<BukkitPlayer>()

    val spectators get() = spectatorList.toList()

    internal fun addSpectator(match: ChessMatch, p: BukkitPlayer) {
        require(p.spectatedMatch == match)
        require(p !in spectatorList)
        spectatorList += p
        match.callEvent(SpectatorEvent(p, PlayerDirection.JOIN))
    }

    internal fun removeSpectator(match: ChessMatch, p: BukkitPlayer) {
        require(p.spectatedMatch == null)
        require(p in spectatorList)
        spectatorList -= p
        match.callEvent(SpectatorEvent(p, PlayerDirection.LEAVE))
    }

    override fun init(match: ChessMatch, events: ChessEventRegistry) {
        events.register(ChessEventType.BASE) {
            if (it == ChessBaseEvent.STOP) stop(match)
            else if (it == ChessBaseEvent.CLEAR) clear(match)
        }
    }

    private fun stop(match: ChessMatch) {
        for (it in spectators) {
            it.sendMatchResults(match.results!!)
        }
    }

    private fun clear(match: ChessMatch) {
        val s = spectators
        spectatorList.clear()
        for (it in s) {
            match.callEvent(SpectatorEvent(it, PlayerDirection.LEAVE))
        }
    }
}

class SpectatorManagerFacade(match: ChessMatch, component: SpectatorManager) : ComponentFacade<SpectatorManager>(match, component) {
    val spectators get() = component.spectators
    internal operator fun plusAssign(p: BukkitPlayer) = component.addSpectator(match, p)
    internal operator fun minusAssign(p: BukkitPlayer) = component.removeSpectator(match, p)
}

val ChessMatch.spectators get() = makeCachedFacade(::SpectatorManagerFacade, require(BukkitComponentType.SPECTATOR_MANAGER))