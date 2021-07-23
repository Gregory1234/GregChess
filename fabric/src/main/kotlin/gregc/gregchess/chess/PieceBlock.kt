package gregc.gregchess.chess

import gregc.gregchess.GregChess
import net.minecraft.block.*
import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.enums.DoubleBlockHalf
import net.minecraft.client.item.TooltipContext
import net.minecraft.entity.ItemEntity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.*
import net.minecraft.nbt.NbtCompound
import net.minecraft.state.StateManager
import net.minecraft.state.property.EnumProperty
import net.minecraft.state.property.Properties
import net.minecraft.text.LiteralText
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
import net.minecraft.world.*
import java.util.*

class PieceBlockEntity(pos: BlockPos?, state: BlockState?) : BlockEntity(GregChess.PIECE_ENTITY_TYPE, pos, state) {
    private var gameUUID: UUID? = null
    val isGameless get() = gameUUID == null
    override fun writeNbt(nbt: NbtCompound): NbtCompound {
        return nbt.apply {
            if (gameUUID != null)
                this.putUuid("GameUUID", gameUUID)
        }
    }
    override fun readNbt(nbt: NbtCompound?) {
        try {
            gameUUID = nbt?.getUuid("GameUUID")
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
    }
}

abstract class PieceBlock(val piece: Piece, settings: Settings?) : BlockWithEntity(settings) {
    override fun getRenderType(state: BlockState?): BlockRenderType = BlockRenderType.MODEL

    override fun createBlockEntity(pos: BlockPos?, state: BlockState?): BlockEntity? = PieceBlockEntity(pos, state)
    override fun getOutlineShape(state: BlockState?, view: BlockView?, pos: BlockPos?, context: ShapeContext?): VoxelShape? =
        VoxelShapes.cuboid(0.125, 0.0, 0.125, 0.875, 1.0, 0.875)

    override fun onBreak(world: World, pos: BlockPos, state: BlockState?, player: PlayerEntity) {
        val blockEntity = world.getBlockEntity(pos)
        if (blockEntity is PieceBlockEntity) {
            if (!world.isClient && player.isCreative && !blockEntity.isGameless) {
                val itemStack = ItemStack(piece.block)
                val nbtCompound = blockEntity.writeNbt(NbtCompound())
                if (!nbtCompound.isEmpty) {
                    itemStack.setSubNbt("BlockEntityTag", nbtCompound)
                }
                val itemEntity = ItemEntity(
                    world, pos.x.toDouble() + 0.5, pos.y.toDouble() + 0.5, pos.z.toDouble() + 0.5, itemStack
                )
                itemEntity.setToDefaultPickupDelay()
                world.spawnEntity(itemEntity)
            }
        }
        super.onBreak(world, pos, state, player)
    }

    override fun appendTooltip(stack: ItemStack, world: BlockView?, tooltip: MutableList<Text>, options: TooltipContext?) {
        val data = stack.getSubNbt("BlockEntityTag")
        try {
            data?.getUuid("GameUUID")?.let {
                tooltip += LiteralText("Game: $it")
            }
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
    }
}

private fun ItemPlacementContext.canPlacePiece(): Boolean {
    val data = stack.getSubNbt("BlockEntityTag")
    try {
        if (data?.getUuid("GameUUID") != null) {
            if (side != Direction.UP)
                return false
            val b = world.getBlockState(blockPos.down())
            if (b.block !is ChessboardFloorBlock)
                return false
        }
    } catch (e: IllegalArgumentException) {
        e.printStackTrace()
        return false
    }
    return true
}

class PawnItem(block: Block?, settings: Settings) : BlockItem(block, settings) {
    override fun canPlace(context: ItemPlacementContext, state: BlockState): Boolean =
        context.canPlacePiece() && super.canPlace(context, state)
}

class TallPieceItem(block: Block?, settings: Settings) : TallBlockItem(block, settings) {
    override fun canPlace(context: ItemPlacementContext, state: BlockState): Boolean =
        context.canPlacePiece() && super.canPlace(context, state)
}

class PawnBlock(piece: Piece, settings: Settings?) : PieceBlock(piece, settings)

class TallPieceBlock(piece: Piece, settings: Settings?) : PieceBlock(piece, settings) {

    companion object {
        @JvmField
        val HALF: EnumProperty<DoubleBlockHalf> = Properties.DOUBLE_BLOCK_HALF
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
        world.setBlockState(pos.up(), defaultState.with(HALF, DoubleBlockHalf.UPPER), 3)
    }

    override fun canPlaceAt(state: BlockState, world: WorldView, pos: BlockPos) =
        (state.get(HALF) != DoubleBlockHalf.UPPER) || run {
            val blockState = world.getBlockState(pos.down())
            blockState.isOf(this) && blockState.get(HALF) == DoubleBlockHalf.LOWER
        }

    override fun createBlockEntity(pos: BlockPos?, state: BlockState?): BlockEntity? =
        if (state?.get(HALF) == DoubleBlockHalf.LOWER) super.createBlockEntity(pos, state) else null

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(HALF)
    }
}