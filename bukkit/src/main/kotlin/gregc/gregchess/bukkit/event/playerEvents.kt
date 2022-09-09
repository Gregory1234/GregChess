package gregc.gregchess.bukkit.event

import gregc.gregchess.bukkit.player.BukkitPlayer
import gregc.gregchess.event.ChessEvent

enum class PlayerDirection {
    JOIN, LEAVE
}

class PlayerEvent(val player: BukkitPlayer, val dir: PlayerDirection) : ChessEvent {
    override val type get() = BukkitChessEventType.PLAYER
}

data class SpectatorEvent(val player: BukkitPlayer, val dir: PlayerDirection) : ChessEvent {
    override val type get() = BukkitChessEventType.SPECTATOR
}