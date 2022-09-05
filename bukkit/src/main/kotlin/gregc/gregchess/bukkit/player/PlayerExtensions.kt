package gregc.gregchess.bukkit.player

import gregc.gregchess.bukkit.match.ChessMatchManager
import gregc.gregchess.match.ChessMatch

val BukkitPlayer.currentSpectatedChessMatch: ChessMatch? get() = ChessMatchManager.currentSpectatedMatchOf(uuid)
val BukkitPlayer.isSpectatingChessMatch: Boolean get() = currentSpectatedChessMatch != null
