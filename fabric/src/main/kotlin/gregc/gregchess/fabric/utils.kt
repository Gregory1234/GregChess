package gregc.gregchess.fabric

import net.minecraft.block.entity.BlockEntity
import net.minecraft.util.Identifier
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

const val MOD_ID = "gregchess"
const val MOD_NAME = "GregChess"

fun ident(name: String) = Identifier(MOD_ID, name)

class BlockEntityDirtyDelegate<T>(var value: T): ReadWriteProperty<BlockEntity, T> {
    override operator fun getValue(thisRef: BlockEntity, property: KProperty<*>): T = value

    override operator fun setValue(thisRef: BlockEntity, property: KProperty<*>, value: T) {
        this.value = value
        thisRef.markDirty()
    }
}