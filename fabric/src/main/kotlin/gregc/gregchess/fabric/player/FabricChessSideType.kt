package gregc.gregchess.fabric.player

import gregc.gregchess.RegisterAll
import gregc.gregchess.player.ChessSideType

@RegisterAll(ChessSideType::class)
object FabricChessSideType {
    @JvmField
    val FABRIC = ChessSideType<FabricChessSide>()
}