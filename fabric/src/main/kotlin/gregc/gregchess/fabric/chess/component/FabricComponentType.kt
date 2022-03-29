package gregc.gregchess.fabric.chess.component

import gregc.gregchess.RegisterAll
import gregc.gregchess.game.ComponentType

@RegisterAll(ComponentType::class)
object FabricComponentType {
    @JvmField
    val RENDERER = ComponentType(FabricRenderer::class)
    @JvmField
    val GAME_CONTROLLER = ComponentType(GameController::class)
}