package gregc.gregchess.chess

import net.minecraft.block.*
import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.enums.DoubleBlockHalf
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemPlacementContext
import net.minecraft.item.ItemStack
import net.minecraft.state.StateManager
import net.minecraft.state.property.EnumProperty
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
import net.minecraft.world.*


class PieceBlock(settings: Settings?) : Block(settings) {

    companion object {
        @JvmField
        val HALF: EnumProperty<DoubleBlockHalf>? = Properties.DOUBLE_BLOCK_HALF
    }

    init {
        defaultState = stateManager.defaultState.with(HALF, DoubleBlockHalf.LOWER)
    }

    override fun getStateForNeighborUpdate(
        state: BlockState,
        direction: Direction,
        neighborState: BlockState,
        world: WorldAccess?,
        pos: BlockPos?,
        neighborPos: BlockPos?
    ): BlockState? {
        val doubleBlockHalf = state.get(HALF)
        return if (direction.axis === Direction.Axis.Y &&
            (doubleBlockHalf == DoubleBlockHalf.LOWER) == (direction == Direction.UP) &&
            (!neighborState.isOf(this) || neighborState.get(HALF) == doubleBlockHalf)
        ) {
            Blocks.AIR.defaultState
        } else if (doubleBlockHalf == DoubleBlockHalf.LOWER && direction == Direction.DOWN &&
            !state.canPlaceAt(world, pos)
        )
            Blocks.AIR.defaultState
        else
            state
    }

    override fun getPlacementState(ctx: ItemPlacementContext): BlockState? {
        val blockPos = ctx.blockPos
        val world = ctx.world
        return if (blockPos.y < world.topY - 1 && world.getBlockState(blockPos.up()).canReplace(ctx))
            super.getPlacementState(ctx)
        else
            null
    }

    override fun onPlaced(world: World, pos: BlockPos, state: BlockState, placer: LivingEntity?, itemStack: ItemStack) {
        val blockPos = pos.up()
        world.setBlockState(blockPos, defaultState.with(HALF, DoubleBlockHalf.UPPER), 3)
    }

    override fun canPlaceAt(state: BlockState, world: WorldView, pos: BlockPos) =
        (state.get(HALF) != DoubleBlockHalf.UPPER) || run {
            val blockState = world.getBlockState(pos.down())
            blockState.isOf(this) && blockState.get(HALF) == DoubleBlockHalf.LOWER
        }

    override fun onBreak(world: World, pos: BlockPos, state: BlockState, player: PlayerEntity) {
        if (!world.isClient) {
            if (state.get(HALF) == DoubleBlockHalf.UPPER) {
                if (player.isCreative) {

                    val blockPos = pos.down()
                    val blockState = world.getBlockState(blockPos)
                    if (blockState.isOf(state.block) && blockState.get(HALF) == DoubleBlockHalf.LOWER) {
                        world.setBlockState(blockPos, Blocks.AIR.defaultState, 35)
                        world.syncWorldEvent(player, 2001, blockPos, getRawIdFromState(blockState))
                    }
                } else {
                    dropStacks(state, world, pos, null as BlockEntity?, player, player.mainHandStack)
                }
            }
        }
        super.onBreak(world, pos, state, player)
    }

    override fun getOutlineShape(
        state: BlockState?,
        view: BlockView?,
        pos: BlockPos?,
        context: ShapeContext?
    ): VoxelShape? {
        return VoxelShapes.cuboid(0.125, 0.0, 0.125, 0.875, 1.0, 0.875)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(HALF)
    }
}