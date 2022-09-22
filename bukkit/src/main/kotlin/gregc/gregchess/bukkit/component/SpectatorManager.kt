package gregc.gregchess.bukkit.component

import gregc.gregchess.bukkit.match.MatchInfoEvent
import gregc.gregchess.bukkit.player.*
import gregc.gregchess.bukkit.results.sendMatchResults
import gregc.gregchess.component.Component
import gregc.gregchess.event.ChessBaseEvent
import gregc.gregchess.event.EventListenerRegistry
import gregc.gregchess.match.ChessMatch
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient


@Serializable
class SpectatorManager : Component {

    override val type get() = BukkitComponentType.SPECTATOR_MANAGER

    @Transient
    private val spectatorList = mutableListOf<BukkitPlayer>()

    val spectators get() = spectatorList.toList()

    private fun addSpectator(match: ChessMatch, p: BukkitPlayer) {
        require(p.spectatedMatch == match)
        require(p !in spectatorList)
        spectatorList += p
    }

    private fun removeSpectator(p: BukkitPlayer) {
        require(p.spectatedMatch == null)
        require(p in spectatorList)
        spectatorList -= p
    }

    override fun init(match: ChessMatch, events: EventListenerRegistry) {
        events.register<ChessBaseEvent> {
            if (it == ChessBaseEvent.STOP) stop(match)
            else if (it == ChessBaseEvent.CLEAR) clear(match)
        }
        events.register<SpectatorEvent> {
            when (it.dir) {
                PlayerDirection.JOIN -> addSpectator(match, it.player)
                PlayerDirection.LEAVE -> removeSpectator(it.player)
            }
        }
        events.registerR<MatchInfoEvent> {
            text("Spectators: ${spectators.joinToString { it.name }}\n")
        }
    }

    private fun stop(match: ChessMatch) {
        for (it in spectators) {
            it.sendMatchResults(match.results!!)
        }
    }

    private fun clear(match: ChessMatch) {
        val s = spectators
        for (it in s) {
            it.leaveSpectatedMatch(match)
        }
        check(spectatorList.isEmpty())
    }
}

val ChessMatch.spectators get() = components.require(BukkitComponentType.SPECTATOR_MANAGER)