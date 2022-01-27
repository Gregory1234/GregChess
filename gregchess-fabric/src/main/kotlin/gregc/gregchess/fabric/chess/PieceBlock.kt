package gregc.gregchess.fabric.chess

import gregc.gregchess.chess.ChessGame
import gregc.gregchess.chess.piece.BoardPiece
import gregc.gregchess.chess.piece.Piece
import gregc.gregchess.fabric.GregChessMod
import gregc.gregchess.fabric.chess.player.FabricChessSide
import gregc.gregchess.fabric.chess.player.gregchess
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
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
import net.minecraft.world.*

class PieceBlockEntity(pos: BlockPos?, state: BlockState?) : BlockEntity(GregChessMod.PIECE_ENTITY_TYPE, pos, state) {
    private var moved: Boolean = false

    val piece: Piece
        get() = (world!!.getBlockState(pos).block as PieceBlock).piece

    val floorBlock get() = world?.getBlockEntity(pos.down()) as? ChessboardFloorBlockEntity

    val chessControllerBlock: ChessControllerBlockEntity? get() = floorBlock?.chessControllerBlock

    val currentGame: ChessGame? get() = chessControllerBlock?.currentGame

    override fun markRemoved() {
        if (world?.isClient == false && !moved) {
            currentGame?.board?.clearPiece(floorBlock!!.boardPos!!)
            currentGame?.board?.updateMoves()
        }
        super.markRemoved()
    }

    fun safeBreak(drop: Boolean = false) {
        moved = true
        world?.breakBlock(pos, drop)
    }

    val exists
        get() = floorBlock?.boardPos?.let { boardPos -> currentGame?.board?.get(boardPos) }?.piece == piece

}

sealed class PieceBlock(val piece: Piece, settings: Settings?) : BlockWithEntity(settings) {
    override fun getRenderType(state: BlockState?): BlockRenderType = BlockRenderType.MODEL

    override fun createBlockEntity(pos: BlockPos?, state: BlockState?): BlockEntity? = PieceBlockEntity(pos, state)
    override fun getOutlineShape(
        state: BlockState?,
        view: BlockView?,
        pos: BlockPos?,
        context: ShapeContext?
    ): VoxelShape? = VoxelShapes.cuboid(0.125, 0.0, 0.125, 0.875, 1.0, 0.875)

    abstract fun canActuallyPlaceAt(world: World?, pos: BlockPos?): Boolean
}

class ShortPieceBlock(piece: Piece, settings: Settings?) : PieceBlock(piece, settings) {
    override fun onUse(
        state: BlockState,
        world: World?,
        pos: BlockPos?,
        player: PlayerEntity,
        hand: Hand,
        hit: BlockHitResult?
    ): ActionResult {
        if (world?.isClient == true) return ActionResult.PASS
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS

        val pieceEntity = world?.getBlockEntity(pos) as? PieceBlockEntity ?: return ActionResult.PASS
        if (!pieceEntity.exists) return ActionResult.PASS
        val game = pieceEntity.currentGame ?: return ActionResult.PASS

        val cp = game.currentSide as? FabricChessSide ?: return ActionResult.PASS

        if (cp.player == player.gregchess && cp.held == null && cp.color == piece.color) {
            cp.pickUp(pieceEntity.floorBlock?.boardPos!!)
            return ActionResult.SUCCESS
        } else if (cp.held?.piece == piece && cp.held?.pos == pieceEntity.floorBlock?.boardPos) {
            return if (cp.makeMove(pieceEntity.floorBlock?.boardPos!!, pieceEntity.floorBlock!!, world.server)) ActionResult.SUCCESS else ActionResult.PASS
        }
        return ActionResult.PASS
    }

    override fun canActuallyPlaceAt(world: World?, pos: BlockPos?): Boolean = true

    override fun onPlaced(world: World, pos: BlockPos, state: BlockState, placer: LivingEntity?, itemStack: ItemStack) {
        if (!world.isClient()) {
            val entity = world.getBlockEntity(pos) as PieceBlockEntity
            entity.currentGame?.board?.plusAssign(BoardPiece(entity.floorBlock!!.boardPos!!, piece, false))
            entity.currentGame?.board?.updateMoves()
        }
    }

    override fun canPlaceAt(state: BlockState, world: WorldView, pos: BlockPos): Boolean {
        val floor = world.getBlockEntity(pos.down(1)) as? ChessboardFloorBlockEntity?
        return floor?.pieceBlock == floor?.directPiece
    }
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
        if (!world.isClient()) {
            val entity = world.getBlockEntity(pos) as PieceBlockEntity
            entity.currentGame?.board?.plusAssign(BoardPiece(entity.floorBlock!!.boardPos!!, piece, false))
            entity.currentGame?.board?.updateMoves()
        }
    }

    override fun canPlaceAt(state: BlockState, world: WorldView, pos: BlockPos) = when(state.get(HALF)) {
        DoubleBlockHalf.UPPER -> {
            val blockState = world.getBlockState(pos.down())
            val floor = world.getBlockEntity(pos.down(2)) as? ChessboardFloorBlockEntity?
            blockState.isOf(this) && blockState.get(HALF) == DoubleBlockHalf.LOWER && floor?.pieceBlock == null
        }
        DoubleBlockHalf.LOWER -> {
            val floor = world.getBlockEntity(pos.down(1)) as? ChessboardFloorBlockEntity?
            floor?.pieceBlock == floor?.directPiece
        }
        null -> false
    }

    override fun createBlockEntity(pos: BlockPos?, state: BlockState?): BlockEntity? =
        if (state?.get(HALF) == DoubleBlockHalf.LOWER) super.createBlockEntity(pos, state) else null

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(HALF)
    }

    override fun onUse(
        state: BlockState,
        world: World?,
        pos: BlockPos?,
        player: PlayerEntity,
        hand: Hand,
        hit: BlockHitResult?
    ): ActionResult {
        if (world?.isClient == true) return ActionResult.PASS
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS
        val realPos = when(state.get(HALF)!!) { DoubleBlockHalf.UPPER -> pos?.down(); DoubleBlockHalf.LOWER -> pos }
        val pieceEntity = (world?.getBlockEntity(realPos) as? PieceBlockEntity) ?: return ActionResult.PASS
        if (!pieceEntity.exists) return ActionResult.PASS
        val game = pieceEntity.currentGame ?: return ActionResult.PASS

        val cp = game.currentSide as? FabricChessSide ?: return ActionResult.PASS

        if (cp.player == player.gregchess && cp.held == null && cp.color == piece.color) {
            cp.pickUp(pieceEntity.floorBlock?.boardPos!!)
            return ActionResult.SUCCESS
        } else if (cp.held?.piece != null) {
            return if (cp.makeMove(pieceEntity.floorBlock?.boardPos!!, pieceEntity.floorBlock!!, world.server)) ActionResult.SUCCESS else ActionResult.PASS
        }
        return ActionResult.PASS
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
                world.setBlockState(blockPos, Blocks.AIR.defaultState, 35)
                world.syncWorldEvent(player, 2001, blockPos, getRawIdFromState(blockState))
            }
        }
    }
}