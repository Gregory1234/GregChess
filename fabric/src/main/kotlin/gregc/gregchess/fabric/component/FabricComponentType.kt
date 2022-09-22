package gregc.gregchess.fabric.component

import gregc.gregchess.RegisterAll
import gregc.gregchess.component.ComponentType
import gregc.gregchess.fabric.renderer.FabricRenderer

@RegisterAll(ComponentType::class)
object FabricComponentType {
    @JvmField
    val RENDERER = ComponentType<FabricRenderer>()
    @JvmField
    val MATCH_CONTROLLER = ComponentType<MatchController>()
}