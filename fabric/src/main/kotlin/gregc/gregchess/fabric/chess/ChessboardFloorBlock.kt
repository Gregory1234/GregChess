package gregc.gregchess.fabric.chess

import gregc.gregchess.chess.Floor
import gregc.gregchess.fabric.BlockEntityDirtyDelegate
import gregc.gregchess.fabric.GregChess
import net.minecraft.block.*
import net.minecraft.block.entity.BlockEntity
import net.minecraft.nbt.NbtCompound
import net.minecraft.state.StateManager
import net.minecraft.state.property.EnumProperty
import net.minecraft.util.StringIdentifiable
import net.minecraft.util.math.BlockPos

class ChessboardFloorBlockEntity(pos: BlockPos?, state: BlockState?) : BlockEntity(GregChess.CHESSBOARD_FLOOR_ENTITY_TYPE, pos, state) {
    var chessControllerBlockPos: BlockPos? by BlockEntityDirtyDelegate(null)
    override fun writeNbt(nbt: NbtCompound): NbtCompound {
        super.writeNbt(nbt)
        chessControllerBlockPos?.let {
            nbt.putLong("controller", it.asLong())
        }
        return nbt
    }
    override fun readNbt(nbt: NbtCompound) {
        super.readNbt(nbt)

        if(nbt.contains("controller", 4)) {
            chessControllerBlockPos = BlockPos.fromLong(nbt.getLong("controller"))
        }
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

    init {
        defaultState = stateManager.defaultState.with(FLOOR, ChessboardFloor.INACTIVE)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(FLOOR)
    }
}