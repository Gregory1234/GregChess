package gregc.gregchess.fabric.match

import gregc.gregchess.RegisterAll
import gregc.gregchess.fabric.renderer.FabricRenderer
import gregc.gregchess.match.ComponentType

@RegisterAll(ComponentType::class)
object FabricComponentType {
    @JvmField
    val RENDERER = ComponentType(FabricRenderer::class)
    @JvmField
    val MATCH_CONTROLLER = ComponentType(MatchController::class)
}