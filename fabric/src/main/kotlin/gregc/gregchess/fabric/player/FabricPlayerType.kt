package gregc.gregchess.fabric.player

import gregc.gregchess.RegisterAll
import gregc.gregchess.player.ChessSideType

// TODO: rename this
@RegisterAll(ChessSideType::class)
object FabricPlayerType {
    @JvmField
    val FABRIC = ChessSideType(FabricChessSide.serializer())
}