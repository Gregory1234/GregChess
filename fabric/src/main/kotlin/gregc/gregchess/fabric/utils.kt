package gregc.gregchess.fabric

import gregc.gregchess.GregLogger
import gregc.gregchess.Loc
import net.minecraft.block.entity.BlockEntity
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import org.apache.logging.log4j.Logger
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal const val MOD_ID = "gregchess"
internal const val MOD_NAME = "GregChess"

internal fun ident(name: String) = Identifier(MOD_ID, name)

class BlockEntityDirtyDelegate<T>(var value: T) : ReadWriteProperty<BlockEntity, T> {
    override operator fun getValue(thisRef: BlockEntity, property: KProperty<*>): T = value

    override operator fun setValue(thisRef: BlockEntity, property: KProperty<*>, value: T) {
        this.value = value
        thisRef.markDirty()
    }
}

class Log4jGregLogger(val logger: Logger): GregLogger {
    override fun info(msg: String) = logger.info(msg)
    override fun warn(msg: String) = logger.warn(msg)
    override fun err(msg: String) = logger.error(msg)
}

val BlockPos.loc get() = Loc(x, y, z)
val Loc.blockpos get() = BlockPos(x, y, z)