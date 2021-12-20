package gregc.gregchess.bukkit.chess.component

import gregc.gregchess.bukkit.chess.player.showGameResults
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.SimpleComponent
import org.bukkit.entity.Player

data class SpectatorEvent(val player: Player, val dir: PlayerDirection) : ChessEvent

class SpectatorNotFoundException(player: Player) : Exception(player.name)

class SpectatorManager(game: ChessGame) : SimpleComponent(game) {

    private val spectatorList = mutableListOf<Player>()

    val spectators get() = spectatorList.toList()

    operator fun plusAssign(p: Player) {
        spectatorList += p
        game.callEvent(SpectatorEvent(p, PlayerDirection.JOIN))
    }

    operator fun minusAssign(p: Player) {
        if (p !in spectatorList)
            throw SpectatorNotFoundException(p)
        spectatorList -= p
        game.callEvent(SpectatorEvent(p, PlayerDirection.LEAVE))
    }

    @ChessEventHandler
    fun onStop(e: GameStopStageEvent) {
        if (e == GameStopStageEvent.STOP) stop()
        else if (e == GameStopStageEvent.CLEAR) clear()
    }

    private fun stop() {
        for (it in spectators) {
            it.showGameResults(game.results!!)
        }
    }

    private fun clear() {
        val s = spectators
        spectatorList.clear()
        for (it in s) {
            game.callEvent(SpectatorEvent(it, PlayerDirection.LEAVE))
        }
    }
}

val ChessGame.spectators get() = requireComponent<SpectatorManager>()