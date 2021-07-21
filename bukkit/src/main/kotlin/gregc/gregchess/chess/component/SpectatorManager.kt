package gregc.gregchess.chess.component

import gregc.gregchess.chess.*
import org.bukkit.entity.Player

data class SpectatorEvent(val human: BukkitPlayer, val dir: PlayerDirection) : ChessEvent {
    val player: Player get() = human.player
}

class SpectatorNotFoundException(val human: BukkitPlayer) : Exception(human.name) {
    val player: Player get() = human.player
}

class SpectatorManager(private val game: ChessGame) : Component {

    object Settings : Component.Settings<SpectatorManager> {
        override fun getComponent(game: ChessGame) = SpectatorManager(game)
    }

    private val spectatorList = mutableListOf<BukkitPlayer>()

    val spectators get() = spectatorList.toList()

    operator fun plusAssign(p: BukkitPlayer) {
        spectatorList += p
        game.components.callEvent(SpectatorEvent(p, PlayerDirection.JOIN))
    }

    operator fun minusAssign(p: BukkitPlayer) {
        if (p !in spectatorList)
            throw SpectatorNotFoundException(p)
        spectatorList -= p
        game.components.callEvent(SpectatorEvent(p, PlayerDirection.LEAVE))
    }

    @ChessEventHandler
    fun handleEvents(e: GameBaseEvent) = when (e) {
        GameBaseEvent.STOP -> stop()
        GameBaseEvent.CLEAR -> clear()
        else -> {}
    }

    private fun stop() {
        spectators.forEach {
            it.showEndReason(game.endReason!!)
        }
    }

    private fun clear() {
        val s = spectators
        spectatorList.clear()
        s.forEach {
            game.components.callEvent(SpectatorEvent(it, PlayerDirection.LEAVE))
        }
    }
}

val ChessGame.spectators get() = requireComponent<SpectatorManager>()