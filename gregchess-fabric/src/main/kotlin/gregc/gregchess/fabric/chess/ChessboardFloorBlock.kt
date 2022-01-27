package gregc.gregchess.fabric.chess

import gregc.gregchess.chess.Pos
import gregc.gregchess.fabric.BlockEntityDirtyDelegate
import gregc.gregchess.fabric.GregChessMod
import gregc.gregchess.fabric.chess.player.FabricChessSide
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
    var chessControllerBlockPos: BlockPos? by BlockEntityDirtyDelegate(null, true)
    var boardPos: Pos? by BlockEntityDirtyDelegate(null, true)
    override fun writeNbt(nbt: NbtCompound) {
        super.writeNbt(nbt)
        chessControllerBlockPos?.let {
            nbt.putLong("Controller", it.asLong())
        }
        boardPos?.let {
            nbt.putLong("Pos", ((it.file.toLong() shl 32) or (it.rank.toLong() and 0xFFFFFFFFL)))
        }
    }

    override fun readNbt(nbt: NbtCompound) {
        super.readNbt(nbt)

        if (nbt.contains("Controller", 4)) {
            chessControllerBlockPos = BlockPos.fromLong(nbt.getLong("Controller"))
        }

        if (nbt.contains("Pos", 4)) {
            val v = nbt.getLong("Pos")
            boardPos = Pos((v shr 32).toInt(), v.toInt())
        }
    }

    fun updateFloor(floor: Floor = boardPos?.let { if ((it.rank + it.file) % 2 == 0) Floor.DARK else Floor.LIGHT } ?: Floor.INACTIVE) {
        if (world?.getBlockState(pos)?.block is ChessboardFloorBlock)
            world?.setBlockState(pos, world!!.getBlockState(pos).with(ChessboardFloorBlock.FLOOR, floor))
    }

    override fun markRemoved() {
        if (world?.isClient == false) {
            chessControllerBlock?.resetBoard()
        }
        super.markRemoved()
    }

    val tileBlocks: Collection<ChessboardFloorBlockEntity>
        get() {
            if (pos == null)
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

    internal val directPiece: PieceBlockEntity?
        get() = world?.getBlockEntity(pos.offset(Direction.UP)) as? PieceBlockEntity

    val pieceBlock: PieceBlockEntity?
        get() = tileBlocks.firstNotNullOfOrNull { it.directPiece }

    val chessControllerBlock: ChessControllerBlockEntity?
        get() = chessControllerBlockPos?.let { world?.getBlockEntity(it) as? ChessControllerBlockEntity }

    override fun toInitialChunkDataNbt(): NbtCompound = NbtCompound().apply {
        boardPos?.let {
            putLong("Pos", ((it.file.toLong() shl 32) or (it.rank.toLong() and 0xFFFFFFFFL)))
        }
    }

    override fun toUpdatePacket(): Packet<ClientPlayPacketListener> = BlockEntityUpdateS2CPacket.create(this)
}

enum class Floor : StringIdentifiable {
    INACTIVE, LIGHT, DARK, MOVE, CAPTURE, SPECIAL, NOTHING, OTHER, LAST_START, LAST_END;

    override fun asString(): String = name.lowercase()
}

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
        val game = floorEntity.chessControllerBlock?.currentGame ?: return ActionResult.PASS

        val cp = game.currentSide as? FabricChessSide ?: return ActionResult.PASS

        if (cp.held != null) {
            return if (cp.makeMove(floorEntity.boardPos!!, floorEntity, world.server)) ActionResult.SUCCESS else ActionResult.PASS
        }
        return ActionResult.PASS
    }
}