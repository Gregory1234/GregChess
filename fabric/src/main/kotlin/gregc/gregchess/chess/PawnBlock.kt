package gregc.gregchess.chess

import net.minecraft.block.*
import net.minecraft.util.math.BlockPos
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
import net.minecraft.world.BlockView

class PawnBlock(settings: Settings?) : Block(settings) {
    override fun getOutlineShape(
        state: BlockState?,
        view: BlockView?,
        pos: BlockPos?,
        context: ShapeContext?
    ): VoxelShape? {
        return VoxelShapes.cuboid(0.125, 0.0, 0.125, 0.875, 1.0, 0.875)
    }
}