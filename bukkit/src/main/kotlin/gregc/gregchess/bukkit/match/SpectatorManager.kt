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

val ChessMatch.spectators get() = require(BukkitComponentType.SPECTATOR_MANAGER)