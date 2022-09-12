package gregc.gregchess.bukkit.component

import gregc.gregchess.bukkit.event.BukkitChessEventType
import gregc.gregchess.bukkit.event.PlayerDirection
import gregc.gregchess.bukkit.player.BukkitPlayer
import gregc.gregchess.bukkit.results.sendMatchResults
import gregc.gregchess.component.Component
import gregc.gregchess.event.*
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

    override fun init(match: ChessMatch, events: ChessEventRegistry) {
        events.register(ChessEventType.BASE) {
            if (it == ChessBaseEvent.STOP) stop(match)
            else if (it == ChessBaseEvent.CLEAR) clear(match)
        }
        events.register(BukkitChessEventType.SPECTATOR) {
            when (it.dir) {
                PlayerDirection.JOIN -> addSpectator(match, it.player)
                PlayerDirection.LEAVE -> removeSpectator(it.player)
            }
        }
        events.registerR(BukkitChessEventType.MATCH_INFO) {
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