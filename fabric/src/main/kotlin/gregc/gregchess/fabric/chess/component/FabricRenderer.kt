package gregc.gregchess.fabric.chess.component

import gregc.gregchess.chess.ChessGame
import gregc.gregchess.chess.component.Component
import gregc.gregchess.fabric.chess.ChessControllerBlockEntity
import gregc.gregchess.fabric.chess.ChessboardFloorBlockEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.WorldAccess

class FabricRenderer(private val game: ChessGame, private val settings: Settings) : Component {
    class Settings(
        val controllerPos: BlockPos,
        val floorBlocks: Collection<ChessboardFloorBlockEntity>,
        val world: WorldAccess
    ) : Component.Settings<FabricRenderer> {
        constructor(controller: ChessControllerBlockEntity) :
                this(controller.pos, controller.floorBlockEntities, controller.world!!)

        override fun getComponent(game: ChessGame) = FabricRenderer(game, this)
    }

}