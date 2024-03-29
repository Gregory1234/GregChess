package gregc.gregchess.fabricutils

import net.minecraft.block.*
import net.minecraft.item.ItemStack
import net.minecraft.server.world.ChunkHolder
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraft.world.event.GameEvent

private fun World.notifyAllAfterMoved(pos: BlockPos, state: BlockState) {
    val worldChunk = getWorldChunk(pos)
    val blockState = worldChunk.getBlockState(pos)
    if (!isClient && worldChunk.levelType != null && worldChunk.levelType.isAfter(ChunkHolder.LevelType.TICKING)) {
        updateListeners(pos, blockState, state, Block.NOTIFY_ALL or Block.MOVED or Block.FORCE_STATE)
    }

    updateNeighbors(pos, blockState?.block)
    if (!isClient && state.hasComparatorOutput()) {
        updateComparators(pos, state.block)
    }

    val i: Int = Block.MOVED or Block.NOTIFY_LISTENERS
    blockState.prepare(this, pos, i, 512 - 1)
    state.updateNeighbors(this, pos, i, 512 - 1)
    state.prepare(this, pos, i, 512 - 1)
}

fun World.moveBlock(poses: Collection<BlockPos>, drop: Boolean): Boolean {
    val rets = poses.map { pos ->
        val blockState = getBlockState(pos)
        if (blockState.isAir) {
            Pair(false, Blocks.AIR.defaultState)
        } else {
            val fluidState = getFluidState(pos)
            if (drop) {
                val blockEntity = if (blockState.hasBlockEntity()) this.getBlockEntity(pos) else null
                Block.dropStacks(blockState, this, pos, blockEntity, null, ItemStack.EMPTY)
            }
            Pair(setBlockState(pos, fluidState.blockState, Block.MOVED or Block.FORCE_STATE, 512), fluidState.blockState)
        }
    }

    poses.zip(rets) { pos, (bl, st) ->
        if (bl) {
            notifyAllAfterMoved(pos, st)
            emitGameEvent(null, GameEvent.BLOCK_DESTROY, pos)
        }
    }

    return rets.all { it.first }
}