package gregc.gregchess.fabric.chess

import gregc.gregchess.chess.Floor
import gregc.gregchess.chess.Pos
import gregc.gregchess.fabric.BlockEntityDirtyDelegate
import gregc.gregchess.fabric.GregChess
import net.minecraft.block.*
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.nbt.NbtCompound
import net.minecraft.state.StateManager
import net.minecraft.state.property.EnumProperty
import net.minecraft.util.StringIdentifiable
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

class ChessboardFloorBlockEntity(pos: BlockPos?, state: BlockState?) : BlockEntity(GregChess.CHESSBOARD_FLOOR_ENTITY_TYPE, pos, state) {
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

        if(nbt.contains("Controller", 4)) {
            chessControllerBlockPos = BlockPos.fromLong(nbt.getLong("Controller"))
        }

        if(nbt.contains("Pos", 4)) {
            val v = nbt.getLong("Pos")
            boardPos = Pos((v shr 32).toInt(), v.toInt())
        }
    }
    fun updateFloor() {
        world?.setBlockState(pos, world!!.getBlockState(pos).with(ChessboardFloorBlock.FLOOR, boardPos?.let {
            if ((it.rank + it.file) % 2 == 0) ChessboardFloor.DARK else ChessboardFloor.LIGHT
        } ?: ChessboardFloor.INACTIVE))
    }
}

enum class ChessboardFloor(val floor: Floor?): StringIdentifiable {
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

    override fun createBlockEntity(pos: BlockPos?, state: BlockState?): BlockEntity = ChessboardFloorBlockEntity(pos, state)

    override fun onBreak(world: World?, pos: BlockPos?, state: BlockState?, player: PlayerEntity?) {
        if (world?.isClient == false) {
            val controllerPos = (world.getBlockEntity(pos) as? ChessboardFloorBlockEntity)?.chessControllerBlockPos
            if (controllerPos != null) {
                (world.getBlockEntity(controllerPos) as? ChessControllerBlockEntity)?.resetBoard()
            }
        }
        super.onBreak(world, pos, state, player)
    }

    init {
        defaultState = stateManager.defaultState.with(FLOOR, ChessboardFloor.INACTIVE)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(FLOOR)
    }
}