package gregc.gregchess.fabric.player

import gregc.gregchess.RegisterAll
import gregc.gregchess.fabric.GameProfileSerializer
import gregc.gregchess.player.ChessSideType

@RegisterAll(ChessSideType::class)
object FabricPlayerType {
    @JvmField
    val FABRIC = ChessSideType(FabricChessSide.serializer())
}