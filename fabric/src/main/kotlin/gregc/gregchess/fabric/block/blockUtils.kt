package gregc.gregchess.fabric.block

import net.minecraft.block.*
import net.minecraft.block.entity.BlockEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.WorldAccess
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.*

class BlockEntityDirtyDelegate<T>(var value: T) : ReadWriteProperty<BlockEntity, T> {
    override operator fun getValue(thisRef: BlockEntity, property: KProperty<*>): T = value

    override operator fun setValue(thisRef: BlockEntity, property: KProperty<*>, value: T) {
        this.value = value
        if (thisRef.world?.isClient == false) {
            thisRef.markDirty()
            (thisRef.world as? ServerWorld)?.chunkManager?.markForUpdate(thisRef.pos)
        }
    }
}

class BlockReference<BE : BlockEntity>(val entityClass: KClass<BE>, private val posRef: () -> BlockPos?, private val worldRef: () -> WorldAccess?, private val posOffset: BlockPos = BlockPos.ORIGIN) {
    val pos get() = posRef()?.add(posOffset)
    val world get() = worldRef()
    val entity get() = pos?.let { world?.getBlockEntity(it) }?.let { entityClass.safeCast(it) }
    val state: BlockState get() = pos?.let { world?.getBlockState(it) } ?: Blocks.AIR.defaultState!!
    val block: Block get() = state.block
    fun offset(off: BlockPos) = BlockReference(entityClass, posRef, worldRef, posOffset.add(off))
    fun <T : BlockEntity> ofEntity(ent: KClass<T>) = BlockReference(ent, posRef, worldRef, posOffset)
}