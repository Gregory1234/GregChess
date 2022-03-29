package gregc.gregchess.fabric.chess.player

import gregc.gregchess.fabric.GameProfileSerializer
import gregc.gregchess.player.ChessPlayerType
import gregc.gregchess.util.RegisterAll

@RegisterAll(ChessPlayerType::class)
object FabricPlayerType {
    @JvmField
    val FABRIC = ChessPlayerType(GameProfileSerializer, { it.name }, ::FabricChessSide)
}