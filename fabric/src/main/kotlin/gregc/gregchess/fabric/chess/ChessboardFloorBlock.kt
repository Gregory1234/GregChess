package gregc.gregchess.fabric.chess

import gregc.gregchess.chess.Floor
import gregc.gregchess.chess.Pos
import gregc.gregchess.fabric.BlockEntityDirtyDelegate
import gregc.gregchess.fabric.GregChess
import net.minecraft.block.*
import net.minecraft.block.entity.BlockEntity
import net.minecraft.nbt.NbtCompound
import net.minecraft.state.StateManager
import net.minecraft.state.property.EnumProperty
import net.minecraft.util.StringIdentifiable
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

class ChessboardFloorBlockEntity(pos: BlockPos?, state: BlockState?) :
    BlockEntity(GregChess.CHESSBOARD_FLOOR_ENTITY_TYPE, pos, state) {
    var chessControllerBlockPos: BlockPos? by BlockEntityDirtyDelegate(null)
    var boardPos: Pos? by BlockEntityDirtyDelegate(null)
    override fun writeNbt(nbt: NbtCompound): NbtCompound {
        super.writeNbt(nbt)
        chessControllerBlockPos?.let {
            nbt.putLong("Controller", it.asLong())
        }
        boardPos?.let {
            nbt.putLong("Pos", ((it.file.toLong() shl 32) or (it.rank.toLong() and 0xFFFFFFFFL)))
        }
        return nbt
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

    fun updateFloor() {
        world?.setBlockState(pos, world!!.getBlockState(pos).with(ChessboardFloorBlock.FLOOR, boardPos?.let {
            if ((it.rank + it.file) % 2 == 0) ChessboardFloor.DARK else ChessboardFloor.LIGHT
        } ?: ChessboardFloor.INACTIVE))
    }

    override fun markRemoved() {
        if (world?.isClient == false) {
            if (chessControllerBlockPos != null) {
                (world?.getBlockEntity(chessControllerBlockPos) as? ChessControllerBlockEntity)?.resetBoard()
            }
        }
        super.markRemoved()
    }

    val tileBlocks: Collection<ChessboardFloorBlockEntity>
        get() {
            if (pos == null)
                return emptyList()
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
            val minx = findOffsets(Direction.WEST)
            val maxx = findOffsets(Direction.EAST)
            val minz = findOffsets(Direction.NORTH)
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
}

enum class ChessboardFloor(val floor: Floor?) : StringIdentifiable {
    INACTIVE(null), LIGHT(Floor.LIGHT), DARK(Floor.DARK), MOVE(Floor.MOVE), CAPTURE(Floor.CAPTURE),
    SPECIAL(Floor.SPECIAL), NOTHING(Floor.NOTHING), OTHER(Floor.OTHER),
    LAST_START(Floor.LAST_START), LAST_END(Floor.LAST_END);

    override fun asString(): String = name.lowercase()
}

class ChessboardFloorBlock(settings: Settings?) : BlockWithEntity(settings) {
    companion object {
        @JvmField
        val FLOOR: EnumProperty<ChessboardFloor> = EnumProperty.of("floor", ChessboardFloor::class.java)
    }

    override fun getRenderType(state: BlockState?): BlockRenderType = BlockRenderType.MODEL

    override fun createBlockEntity(pos: BlockPos?, state: BlockState?): BlockEntity =
        ChessboardFloorBlockEntity(pos, state)

    init {
        defaultState = stateManager.defaultState.with(FLOOR, ChessboardFloor.INACTIVE)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(FLOOR)
    }
}