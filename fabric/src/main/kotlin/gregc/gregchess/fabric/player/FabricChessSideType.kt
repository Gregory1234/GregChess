package gregc.gregchess.fabric.player

import gregc.gregchess.player.ChessSideType
import gregc.gregchess.registry.RegisterAll

@RegisterAll(ChessSideType::class)
object FabricChessSideType {
    @JvmField
    val FABRIC = ChessSideType<FabricChessSide>()
}