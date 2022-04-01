package gregc.gregchess.fabric

import net.minecraft.block.AbstractFireBlock
import net.minecraft.block.Block
import net.minecraft.item.ItemStack
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraft.world.event.GameEvent

internal const val MOD_ID = "gregchess"
internal const val MOD_NAME = "GregChess"

internal fun ident(name: String) = Identifier(MOD_ID, name)

fun World.moveBlock(pos: BlockPos, drop: Boolean): Boolean {
    val blockState = getBlockState(pos)
    return if (blockState.isAir) {
        false
    } else {
        val fluidState = getFluidState(pos)
        if (blockState.block !is AbstractFireBlock) {
            syncWorldEvent(2001, pos, Block.getRawIdFromState(blockState))
        }
        if (drop) {
            val blockEntity = if (blockState.hasBlockEntity()) this.getBlockEntity(pos) else null
            Block.dropStacks(blockState, this, pos, blockEntity, null, ItemStack.EMPTY)
        }
        val bl = setBlockState(pos, fluidState.blockState, Block.NOTIFY_ALL or Block.MOVED, 512)
        if (bl) {
            emitGameEvent(null, GameEvent.BLOCK_DESTROY, pos)
        }
        bl
    }
}