package gregc.gregchess.fabric

import gregc.gregchess.Pos
import gregc.gregchess.registry.*
import net.minecraft.util.Identifier

val NameRegistered.id get() = Identifier(module.namespace, name)

fun Pos.toLong() = (file.toLong() shl 32) or rank.toLong()

fun Pos.Companion.fromLong(v: Long) = Pos((v shr 32).toInt(), v.toInt())
