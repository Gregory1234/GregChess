package gregc.gregchess.fabric.chess.player

import gregc.gregchess.chess.player.ChessPlayerType
import gregc.gregchess.fabric.GameProfileSerializer
import gregc.gregchess.registry.RegisterAll

@RegisterAll(ChessPlayerType::class)
object FabricPlayerType {
    @JvmField
    val FABRIC = ChessPlayerType(GameProfileSerializer, { it.name }, ::FabricChessSide)
}