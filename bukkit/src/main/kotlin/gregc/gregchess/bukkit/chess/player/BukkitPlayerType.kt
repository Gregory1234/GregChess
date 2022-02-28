package gregc.gregchess.bukkit.chess.player

import gregc.gregchess.chess.player.ChessPlayerType
import gregc.gregchess.registry.RegisterAll

@RegisterAll(ChessPlayerType::class)
object BukkitPlayerType {
    @JvmField
    val BUKKIT = ChessPlayerType(BukkitPlayer.serializer())
    @JvmField
    val STOCKFISH = ChessPlayerType(Stockfish.serializer())
}