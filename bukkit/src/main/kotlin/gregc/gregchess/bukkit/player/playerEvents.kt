package gregc.gregchess.bukkit.player

import gregc.gregchess.event.ChessEvent

enum class PlayerDirection {
    JOIN, LEAVE
}

class PlayerEvent(val player: BukkitPlayer, val dir: PlayerDirection) : ChessEvent

data class SpectatorEvent(val player: BukkitPlayer, val dir: PlayerDirection) : ChessEvent