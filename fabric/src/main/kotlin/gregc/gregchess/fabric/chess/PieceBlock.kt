package gregc.gregchess.fabric.chess

import gregc.gregchess.fabric.chess.player.FabricChessSide
import gregc.gregchess.fabric.chess.player.gregchess
import gregc.gregchess.move.phantomClear
import gregc.gregchess.move.phantomSpawn
import gregc.gregchess.piece.BoardPiece
import gregc.gregchess.piece.Piece
import net.minecraft.block.*
import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.enums.DoubleBlockHalf
import net.minecraft.block.piston.PistonBehavior
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemPlacementContext
import net.minecraft.item.ItemStack
import net.minecraft.state.StateManager
import net.minecraft.state.property.EnumProperty
import net.minecraft.state.property.Properties
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
import net.minecraft.world.*

sealed class PieceBlock(val piece: Piece, settings: Settings?) : Block(settings) {
    override fun getRenderType(state: BlockState?): BlockRenderType = BlockRenderType.MODEL

    override fun getOutlineShape(
        state: BlockState?,
        view: BlockView?,
        pos: BlockPos?,
        context: ShapeContext?
    ): VoxelShape? = VoxelShapes.cuboid(0.125, 0.0, 0.125, 0.875, 1.0, 0.875)

    abstract fun canActuallyPlaceAt(world: World?, pos: BlockPos?): Boolean

    abstract fun getFloor(world: World, pos: BlockPos, state: BlockState): ChessboardFloorBlockEntity?

    override fun onUse(
        state: BlockState,
        world: World,
        pos: BlockPos,
        player: PlayerEntity,
        hand: Hand,
        hit: BlockHitResult?
    ): ActionResult {
        if (world.isClient) return ActionResult.PASS
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS

        if (!exists(world, pos, state)) return ActionResult.PASS

        val floor = getFloor(world, pos, state) ?: return ActionResult.PASS
        val game = floor.chessControllerBlock.entity?.currentGame ?: return ActionResult.PASS

        val cp = game.currentSide as? FabricChessSide ?: return ActionResult.PASS

        if (cp.player == player.gregchess && cp.held == null && cp.color == piece.color) {
            cp.pickUp(floor.boardPos!!)
            return ActionResult.SUCCESS
        } else if (cp.held?.piece == piece && cp.held?.pos == floor.boardPos) {
            return if (cp.makeMove(floor.boardPos!!, floor, world.server)) ActionResult.SUCCESS else ActionResult.PASS
        }
        return ActionResult.PASS
    }

    fun exists(world: World, pos: BlockPos, state: BlockState): Boolean {
        val floor = getFloor(world, pos, state) ?: return false
        return floor.chessControllerBlock.entity?.currentGame?.board?.get(floor.boardPos!!)?.piece == piece
    }

    override fun getPistonBehavior(state: BlockState?): PistonBehavior = PistonBehavior.BLOCK
}

class ShortPieceBlock(piece: Piece, settings: Settings?) : PieceBlock(piece, settings) {

    override fun canActuallyPlaceAt(world: World?, pos: BlockPos?): Boolean = true

    override fun onPlaced(world: World, pos: BlockPos, state: BlockState, placer: LivingEntity?, itemStack: ItemStack) {
        if (!world.isClient()) {
            val floor = getFloor(world, pos, state) ?: return
            floor.chessControllerBlock.entity?.currentGame?.finishMove(phantomSpawn(BoardPiece(floor.boardPos!!, piece, false)))
        }
    }

    override fun canPlaceAt(state: BlockState, world: WorldView, pos: BlockPos): Boolean {
        val floor = world.getBlockEntity(pos.down(1)) as? ChessboardFloorBlockEntity?
        return floor?.pieceBlock?.pos?.takeIf { floor.pieceBlock.block is PieceBlock } == floor?.directPiece?.pos?.takeIf { floor.directPiece.block is PieceBlock }
    }

    override fun getFloor(world: World, pos: BlockPos, state: BlockState) =
        world.getBlockEntity(pos.down()) as? ChessboardFloorBlockEntity
}

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
        val doubleBlockHalf = state[HALF]
        return if (direction.axis === Direction.Axis.Y &&
            (doubleBlockHalf == DoubleBlockHalf.LOWER) == (direction == Direction.UP) &&
            (!neighborState.isOf(this) || neighborState[HALF] == doubleBlockHalf)
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
        world.setBlockState(pos.up(), defaultState.with(HALF, DoubleBlockHalf.UPPER), NOTIFY_ALL)
        if (!world.isClient()) {
            val floor = getFloor(world, pos, state) ?: return
            floor.chessControllerBlock.entity?.currentGame?.finishMove(phantomSpawn(BoardPiece(floor.boardPos!!, piece, false)))
        }
    }

    override fun canPlaceAt(state: BlockState, world: WorldView, pos: BlockPos) = when(state[HALF]) {
        DoubleBlockHalf.UPPER -> {
            val blockState = world.getBlockState(pos.down())
            val floor = world.getBlockEntity(pos.down(2)) as? ChessboardFloorBlockEntity?
            blockState.isOf(this) && blockState[HALF] == DoubleBlockHalf.LOWER && floor?.pieceBlock?.block is PieceBlock
        }
        DoubleBlockHalf.LOWER -> {
            val floor = world.getBlockEntity(pos.down(1)) as? ChessboardFloorBlockEntity?
            floor?.pieceBlock?.pos?.takeIf { floor.pieceBlock.block is PieceBlock } == floor?.directPiece?.pos?.takeIf { floor.directPiece.block is PieceBlock }
        }
        null -> false
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(HALF)
    }

    override fun canActuallyPlaceAt(world: World?, pos: BlockPos?): Boolean =
        world != null && pos != null && pos.y < world.topY - 1 &&
                (world.getBlockState(pos.up()).material.isReplaceable
                        || world.getBlockState(pos.up()).let { it.block is TallPieceBlock && it.get(HALF) == DoubleBlockHalf.UPPER})

    override fun onBreak(world: World, pos: BlockPos, state: BlockState, player: PlayerEntity) {
        if (!world.isClient) {
            if (player.isCreative) {
                onBreakInCreative(world, pos, state, player)
            } else {
                dropStacks(state, world, pos, null as BlockEntity?, player, player.mainHandStack)
            }
        }
        super.onBreak(world, pos, state, player)
    }

    private fun onBreakInCreative(world: World, pos: BlockPos, state: BlockState, player: PlayerEntity?) {
        if (state.get(HALF) == DoubleBlockHalf.UPPER) {
            val blockPos = pos.down()
            val blockState = world.getBlockState(blockPos)
            if (blockState.isOf(state.block) && blockState.get(HALF) == DoubleBlockHalf.LOWER) {
                world.setBlockState(blockPos, Blocks.AIR.defaultState, NOTIFY_ALL or SKIP_DROPS)
                world.syncWorldEvent(player, 2001, blockPos, getRawIdFromState(blockState))
            }
        }
    }

    override fun getFloor(world: World, pos: BlockPos, state: BlockState) =
        world.getBlockEntity(if (state[HALF] == DoubleBlockHalf.UPPER) pos.down(2) else pos.down()) as? ChessboardFloorBlockEntity

    @Suppress("DEPRECATION")
    override fun onStateReplaced(state: BlockState, world: World, pos: BlockPos, newState: BlockState, moved: Boolean) {
        if (!moved && state[HALF] == DoubleBlockHalf.LOWER) {
            val floor = getFloor(world, pos, state)
            val currentGame = floor?.chessControllerBlock?.entity?.currentGame
            if (floor != null && currentGame != null) {
                val piece = currentGame.board[floor.boardPos!!]
                currentGame.finishMove(phantomClear(piece!!))
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved)
    }
}