package gregc.gregchess.fabric.chess.component

import gregc.gregchess.Loc
import gregc.gregchess.chess.ChessGame
import gregc.gregchess.chess.component.Component
import gregc.gregchess.chess.component.ComponentData
import gregc.gregchess.fabric.blockpos
import gregc.gregchess.fabric.chess.ChessControllerBlockEntity
import gregc.gregchess.fabric.chess.ChessboardFloorBlockEntity
import gregc.gregchess.fabric.loc
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import net.minecraft.world.World

@Serializable
data class FabricRendererSettings(
    val controllerLoc: Loc,
    val world: @Contextual World
) : ComponentData<FabricRenderer> {
    constructor(controller: ChessControllerBlockEntity) :
            this(controller.pos.loc, controller.world!!)

    val controller: ChessControllerBlockEntity
        get() = world.getBlockEntity(controllerLoc.blockpos) as ChessControllerBlockEntity

    val floor: List<ChessboardFloorBlockEntity> get() = controller.floorBlockEntities

    override fun getComponent(game: ChessGame) = FabricRenderer(game, this)
}

class FabricRenderer(game: ChessGame, override val data: FabricRendererSettings) : Component(game)

val ChessGame.renderer get() = requireComponent<FabricRenderer>()