package gregc.gregchess.fabric.block

import net.minecraft.nbt.NbtCompound
import java.util.*

// TODO: make things internal or move to a separate gradle module

fun NbtCompound.ensureNotEmpty() = apply {
    if (isEmpty)
        putBoolean("empty", true)
}

fun NbtCompound.contains(key: String, type: Byte) = contains(key, type.toInt())

fun NbtCompound.getLongOrNull(key: String): Long? = if (contains(key, NbtCompound.LONG_TYPE)) getLong(key) else null

fun NbtCompound.putLongOrNull(key: String, value: Long?) {
    value?.let { putLong(key, it) }
}

fun NbtCompound.getUuidOrNull(key: String): UUID? = if (containsUuid(key)) getUuid(key) else null

fun NbtCompound.putUuidOrNull(key: String, value: UUID?) {
    value?.let { putUuid(key, it) }
}