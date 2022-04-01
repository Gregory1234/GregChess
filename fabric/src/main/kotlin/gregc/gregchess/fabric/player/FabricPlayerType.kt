package gregc.gregchess.fabric.player

import gregc.gregchess.RegisterAll
import gregc.gregchess.fabric.GameProfileSerializer
import gregc.gregchess.player.ChessPlayerType

@RegisterAll(ChessPlayerType::class)
object FabricPlayerType {
    @JvmField
    val FABRIC = ChessPlayerType(GameProfileSerializer, { it.name }, ::FabricChessSide)
}