package gregc.gregchess.bukkit.match

import gregc.gregchess.bukkit.player.showMatchResults
import gregc.gregchess.match.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.bukkit.entity.Player

data class SpectatorEvent(val player: Player, val dir: PlayerDirection) : ChessEvent {
    override val type get() = BukkitChessEventType.SPECTATOR
}

class SpectatorNotFoundException(player: Player) : Exception(player.name)

@Serializable
class SpectatorManager : Component { // TODO: consider reworking the spectator system

    override val type get() = BukkitComponentType.SPECTATOR_MANAGER

    @Transient
    private val spectatorList = mutableListOf<Player>()

    val spectators get() = spectatorList.toList()

    fun addSpectator(match: ChessMatch, p: Player) {
        ChessMatchManager.setCurrentSpectatedMatch(p.uniqueId, match.uuid) // TODO: this shouldn't be called here probably
        spectatorList += p
        match.callEvent(SpectatorEvent(p, PlayerDirection.JOIN))
    }

    fun removeSpectator(match: ChessMatch, p: Player) {
        if (p !in spectatorList)
            throw SpectatorNotFoundException(p)
        ChessMatchManager.setCurrentSpectatedMatch(p.uniqueId, null)
        spectatorList -= p
        match.callEvent(SpectatorEvent(p, PlayerDirection.LEAVE))
    }

    override fun init(match: ChessMatch, eventManager: ChessEventManager) {
        eventManager.registerEvent(ChessEventType.BASE) {
            if (it == ChessBaseEvent.STOP) stop(match)
            else if (it == ChessBaseEvent.CLEAR) clear(match)
        }
    }

    private fun stop(match: ChessMatch) {
        for (it in spectators) {
            it.showMatchResults(match.results!!)
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
    operator fun plusAssign(p: Player) = component.addSpectator(match, p)
    operator fun minusAssign(p: Player) = component.removeSpectator(match, p)
}

val ChessMatch.spectators get() = makeCachedFacade(::SpectatorManagerFacade, require(BukkitComponentType.SPECTATOR_MANAGER))