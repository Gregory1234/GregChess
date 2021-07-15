package gregc.gregchess.chess

import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.state.StateManager
import net.minecraft.state.property.EnumProperty
import net.minecraft.util.StringIdentifiable

enum class ChessboardFloor(val floor: Floor?): StringIdentifiable {
    INACTIVE(null), LIGHT(Floor.LIGHT), DARK(Floor.DARK), MOVE(Floor.MOVE), CAPTURE(Floor.CAPTURE),
    SPECIAL(Floor.SPECIAL), NOTHING(Floor.NOTHING), OTHER(Floor.OTHER),
    LAST_START(Floor.LAST_START), LAST_END(Floor.LAST_END);

    override fun asString(): String = name.lowercase()
}

class ChessboardFloorBlock(settings: Settings?) : Block(settings) {
    companion object {
        @JvmField
        val FLOOR: EnumProperty<ChessboardFloor> = EnumProperty.of("floor", ChessboardFloor::class.java)
    }

    init {
        defaultState = stateManager.defaultState.with(FLOOR, ChessboardFloor.INACTIVE)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(FLOOR)
    }
}