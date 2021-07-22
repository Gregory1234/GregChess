package gregc.gregchess.chess.component

import gregc.gregchess.chess.*

data class SpectatorEvent(val human: HumanPlayer, val dir: PlayerDirection) : ChessEvent

class SpectatorNotFoundException(val human: HumanPlayer) : Exception(human.name)

class SpectatorManager(private val game: ChessGame) : Component {

    object Settings : Component.Settings<SpectatorManager> {
        override fun getComponent(game: ChessGame) = SpectatorManager(game)
    }

    private val spectatorList = mutableListOf<HumanPlayer>()

    val spectators get() = spectatorList.toList()

    operator fun plusAssign(p: HumanPlayer) {
        spectatorList += p
        game.components.callEvent(SpectatorEvent(p, PlayerDirection.JOIN))
    }

    operator fun minusAssign(p: HumanPlayer) {
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
            it.showEndReason(game.end!!)
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