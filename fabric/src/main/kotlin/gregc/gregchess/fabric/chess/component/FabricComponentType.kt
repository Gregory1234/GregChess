package gregc.gregchess.fabric.chess.component

import gregc.gregchess.chess.component.ComponentType
import gregc.gregchess.registry.RegisterAll

@RegisterAll(ComponentType::class)
object FabricComponentType {
    @JvmField
    val RENDERER = ComponentType(FabricRenderer::class)
    @JvmField
    val GAME_CONTROLLER = ComponentType(GameController::class)
}