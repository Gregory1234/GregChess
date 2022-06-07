package gregc.gregchess.bukkit.game

import gregc.gregchess.bukkit.player.showGameResults
import gregc.gregchess.game.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.bukkit.entity.Player

data class SpectatorEvent(val player: Player, val dir: PlayerDirection) : ChessEvent

class SpectatorNotFoundException(player: Player) : Exception(player.name)

@Serializable
class SpectatorManager : Component { // TODO: consider reworking the spectator system

    override val type get() = BukkitComponentType.SPECTATOR_MANAGER

    @Transient
    private lateinit var game: ChessGame

    override fun init(game: ChessGame) {
        this.game = game
    }

    @Transient
    private val spectatorList = mutableListOf<Player>()

    val spectators get() = spectatorList.toList()

    operator fun plusAssign(p: Player) {
        ChessGameManager.setCurrentSpectatedGame(p.uniqueId, game.uuid)
        spectatorList += p
        game.callEvent(SpectatorEvent(p, PlayerDirection.JOIN))
    }

    operator fun minusAssign(p: Player) {
        if (p !in spectatorList)
            throw SpectatorNotFoundException(p)
        ChessGameManager.setCurrentSpectatedGame(p.uniqueId, null)
        spectatorList -= p
        game.callEvent(SpectatorEvent(p, PlayerDirection.LEAVE))
    }

    @ChessEventHandler
    fun onStop(e: GameBaseEvent) {
        if (e == GameBaseEvent.STOP) stop()
        else if (e == GameBaseEvent.CLEAR) clear()
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

val ChessGame.spectators get() = require(BukkitComponentType.SPECTATOR_MANAGER)
val ComponentHolder.spectators get() = get(BukkitComponentType.SPECTATOR_MANAGER)