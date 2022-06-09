package gregc.gregchess.fabric.block

import gregc.gregchess.Pos
import gregc.gregchess.fabric.*
import gregc.gregchess.fabric.piece.PieceBlock
import gregc.gregchess.fabric.player.FabricChessSide
import net.minecraft.block.*
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.nbt.NbtCompound
import net.minecraft.network.Packet
import net.minecraft.network.listener.ClientPlayPacketListener
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket
import net.minecraft.state.StateManager
import net.minecraft.state.property.EnumProperty
import net.minecraft.util.*
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.World


class ChessboardFloorBlockEntity(pos: BlockPos?, state: BlockState?) : BlockEntity(GregChessMod.CHESSBOARD_FLOOR_ENTITY_TYPE, pos, state) {
    private var chessControllerBlockPos: BlockPos? by BlockEntityDirtyDelegate(null)
    var boardPos: Pos? by BlockEntityDirtyDelegate(null)
        private set

    val chessControllerBlock = BlockReference(ChessControllerBlockEntity::class, { chessControllerBlockPos }, { world })

    override fun writeNbt(nbt: NbtCompound) {
        nbt.putLongOrNull("Controller", chessControllerBlockPos?.asLong())
        nbt.putLongOrNull("Pos", boardPos?.toLong())
    }

    override fun readNbt(nbt: NbtCompound) {
        chessControllerBlockPos = nbt.getLongOrNull("Controller")?.let(BlockPos::fromLong)
        boardPos = nbt.getLongOrNull("Pos")?.let(Pos::fromLong)
    }

    fun updateFloor(floor: Floor = boardPos?.let { if ((it.rank + it.file) % 2 == 0) Floor.DARK else Floor.LIGHT } ?: Floor.INACTIVE) {
        if (world?.getBlockState(pos)?.block is ChessboardFloorBlock)
            world?.setBlockState(pos, world!!.getBlockState(pos).with(ChessboardFloorBlock.FLOOR, floor))
    }

    fun reset() {
        chessControllerBlockPos = null
        boardPos = null
        updateFloor()
    }

    fun register(controller: BlockPos, bp: Pos) {
        chessControllerBlockPos = controller
        boardPos = bp
        updateFloor()
    }

    val tileBlocks: Collection<ChessboardFloorBlockEntity>
        get() {
            if (boardPos == null)
                return listOf(this)
            fun findOffsets(d: Direction): Int {
                var off = 1
                while (true) {
                    val ent = world?.getBlockEntity(pos.offset(d, off)) as? ChessboardFloorBlockEntity
                    if (ent == null || ent.boardPos != boardPos)
                        return off - 1
                    else
                        off++
                }
            }
            val minx = -findOffsets(Direction.WEST)
            val maxx = findOffsets(Direction.EAST)
            val minz = -findOffsets(Direction.NORTH)
            val maxz = findOffsets(Direction.SOUTH)
            return buildList {
                for (x in minx..maxx)
                    for (z in minz..maxz)
                        (world?.getBlockEntity(pos.offset(Direction.Axis.X, x).offset(Direction.Axis.Z, z))
                                as? ChessboardFloorBlockEntity)?.let { add(it) }
            }
        }

    internal val directPiece = BlockReference(Nothing::class, { this.pos?.up() }, { world })

    val pieceBlock = BlockReference(Nothing::class, { tileBlocks.firstOrNull { it.directPiece.block is PieceBlock }?.directPiece?.pos }, { world })

    override fun toInitialChunkDataNbt(): NbtCompound = createNbt().ensureNotEmpty()

    override fun toUpdatePacket(): Packet<ClientPlayPacketListener> = BlockEntityUpdateS2CPacket.create(this)
}

enum class Floor : StringIdentifiable {
    INACTIVE, LIGHT, DARK, MOVE, CAPTURE, SPECIAL, NOTHING, OTHER, LAST_START, LAST_END;

    override fun asString(): String = name.lowercase()
}

@Suppress("OVERRIDE_DEPRECATION")
class ChessboardFloorBlock(settings: Settings?) : BlockWithEntity(settings) {
    companion object {
        @JvmField
        val FLOOR: EnumProperty<Floor> = EnumProperty.of("floor", Floor::class.java)
    }

    override fun getRenderType(state: BlockState?): BlockRenderType = BlockRenderType.MODEL

    override fun createBlockEntity(pos: BlockPos?, state: BlockState?): BlockEntity =
        ChessboardFloorBlockEntity(pos, state)

    init {
        defaultState = stateManager.defaultState.with(FLOOR, Floor.INACTIVE)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(FLOOR)
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
        val floorEntity = (world?.getBlockEntity(pos) as? ChessboardFloorBlockEntity) ?: return ActionResult.PASS
        val match = floorEntity.chessControllerBlock.entity?.currentMatch ?: return ActionResult.PASS

        val cp = match.currentSide as? FabricChessSide ?: return ActionResult.PASS

        if (cp.held != null) {
            return if (cp.makeMove(floorEntity.boardPos!!, floorEntity, world.server)) ActionResult.SUCCESS else ActionResult.PASS
        }
        return ActionResult.PASS
    }

    @Suppress("DEPRECATION")
    override fun onStateReplaced(state: BlockState, world: World, pos: BlockPos, newState: BlockState, moved: Boolean) {
        if (!newState.isOf(this)) {
            val entity = world.getBlockEntity(pos) as? ChessboardFloorBlockEntity
            entity?.chessControllerBlock?.entity?.resetBoard()
        }
        super.onStateReplaced(state, world, pos, newState, moved)
    }
}