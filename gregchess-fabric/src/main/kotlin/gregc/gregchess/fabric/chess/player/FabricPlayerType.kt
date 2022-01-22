package gregc.gregchess.fabric.chess.player

import gregc.gregchess.chess.player.ChessPlayerType
import gregc.gregchess.registry.RegisterAll

@RegisterAll(ChessPlayerType::class)
object FabricPlayerType {
    @JvmField
    val FABRIC = ChessPlayerType(FabricPlayer::class)
}