package gregc.gregchess.bukkit.chess.component

import gregc.gregchess.bukkit.chess.BukkitPlayer
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Component

data class SpectatorEvent(val human: BukkitPlayer, val dir: PlayerDirection) : ChessEvent

class SpectatorNotFoundException(human: BukkitPlayer) : Exception(human.name)

class SpectatorManager(private val game: ChessGame) : Component {

    object Settings : Component.Settings<SpectatorManager> {
        override fun getComponent(game: ChessGame) = SpectatorManager(game)
    }

    private val spectatorList = mutableListOf<BukkitPlayer>()

    val spectators get() = spectatorList.toList()

    operator fun plusAssign(p: BukkitPlayer) {
        spectatorList += p
        game.callEvent(SpectatorEvent(p, PlayerDirection.JOIN))
    }

    operator fun minusAssign(p: BukkitPlayer) {
        if (p !in spectatorList)
            throw SpectatorNotFoundException(p)
        spectatorList -= p
        game.callEvent(SpectatorEvent(p, PlayerDirection.LEAVE))
    }

    @ChessEventHandler
    fun onStop(e: GameStopStageEvent) = when (e) {
        GameStopStageEvent.STOP -> stop()
        GameStopStageEvent.CLEAR -> clear()
        else -> {
        }
    }

    private fun stop() {
        spectators.forEach {
            it.showGameResults(game.results!!)
        }
    }

    private fun clear() {
        val s = spectators
        spectatorList.clear()
        s.forEach {
            game.callEvent(SpectatorEvent(it, PlayerDirection.LEAVE))
        }
    }
}

val ChessGame.spectators get() = requireComponent<SpectatorManager>()