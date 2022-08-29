package gregc.gregchess.bukkit.match

import gregc.gregchess.bukkit.player.BukkitPlayer
import gregc.gregchess.bukkit.player.showMatchResults
import gregc.gregchess.match.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

data class SpectatorEvent(val player: BukkitPlayer, val dir: PlayerDirection) : ChessEvent {
    override val type get() = BukkitChessEventType.SPECTATOR
}

class SpectatorNotFoundException(player: BukkitPlayer) : Exception(player.name)

@Serializable
class SpectatorManager : Component { // TODO: consider reworking the spectator system

    override val type get() = BukkitComponentType.SPECTATOR_MANAGER

    @Transient
    private val spectatorList = mutableListOf<BukkitPlayer>()

    val spectators get() = spectatorList.toList()

    fun addSpectator(match: ChessMatch, p: BukkitPlayer) {
        ChessMatchManager.setCurrentSpectatedMatch(p.uuid, match.uuid) // TODO: this shouldn't be called here probably
        spectatorList += p
        match.callEvent(SpectatorEvent(p, PlayerDirection.JOIN))
    }

    fun removeSpectator(match: ChessMatch, p: BukkitPlayer) {
        if (p !in spectatorList)
            throw SpectatorNotFoundException(p)
        ChessMatchManager.setCurrentSpectatedMatch(p.uuid, null)
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
    operator fun plusAssign(p: BukkitPlayer) = component.addSpectator(match, p)
    operator fun minusAssign(p: BukkitPlayer) = component.removeSpectator(match, p)
}

val ChessMatch.spectators get() = makeCachedFacade(::SpectatorManagerFacade, require(BukkitComponentType.SPECTATOR_MANAGER))