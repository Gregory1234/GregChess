package gregc.gregchess.fabric.chess.component

import gregc.gregchess.chess.ChessGame
import gregc.gregchess.chess.component.Component
import gregc.gregchess.chess.component.ComponentData
import gregc.gregchess.fabric.chess.ChessControllerBlockEntity
import gregc.gregchess.fabric.chess.ChessboardFloorBlockEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.WorldAccess

class FabricRendererSettings(
    val controllerPos: BlockPos,
    val floorBlocks: Collection<ChessboardFloorBlockEntity>,
    val world: WorldAccess
) : ComponentData<FabricRenderer> {
    constructor(controller: ChessControllerBlockEntity) :
            this(controller.pos, controller.floorBlockEntities, controller.world!!)

    override fun getComponent(game: ChessGame) = FabricRenderer(game, this)
}

class FabricRenderer(game: ChessGame, override val data: FabricRendererSettings) : Component(game)