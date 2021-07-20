package gregc.gregchess.chess.component

import gregc.gregchess.chess.*

data class SpectatorJoinEvent(val player: BukkitPlayer) : ChessEvent
data class SpectatorLeaveEvent(val player: BukkitPlayer) : ChessEvent

class SpectatorNotFoundException(val player: BukkitPlayer) : Exception(player.name)

class SpectatorManager(private val game: ChessGame) : Component {

    object Settings : Component.Settings<SpectatorManager> {
        override fun getComponent(game: ChessGame) = SpectatorManager(game)
    }

    private val spectatorList = mutableListOf<BukkitPlayer>()

    val spectators get() = spectatorList.toList()

    operator fun plusAssign(p: BukkitPlayer) {
        spectatorList += p
        game.components.callEvent(SpectatorJoinEvent(p))
    }

    operator fun minusAssign(p: BukkitPlayer) {
        if (p !in spectatorList)
            throw SpectatorNotFoundException(p)
        spectatorList -= p
        game.components.callEvent(SpectatorLeaveEvent(p))
    }

    @GameEvent(GameBaseEvent.STOP)
    fun stop(reason: EndReason) {
        spectators.forEach {
            it.showEndReason(reason)
        }
    }

    @GameEvent(GameBaseEvent.CLEAR)
    fun clear() {
        val s = spectators
        spectatorList.clear()
        s.forEach {
            game.components.callEvent(SpectatorLeaveEvent(it))
        }
    }
}

val ChessGame.spectators get() = requireComponent<SpectatorManager>()