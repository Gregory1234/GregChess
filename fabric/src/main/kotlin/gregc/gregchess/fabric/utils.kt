package gregc.gregchess.fabric

import gregc.gregchess.Pos
import gregc.gregchess.registry.*
import net.minecraft.util.Identifier

internal const val MOD_ID = "gregchess"
internal const val MOD_NAME = "GregChess"

internal fun ident(name: String) = Identifier(MOD_ID, name)

val NameRegistered.id get() = Identifier(module.namespace, name)

fun Pos.toLong() = (file.toLong() shl 32) or rank.toLong()

fun Pos.Companion.fromLong(v: Long) = Pos((v shr 32).toInt(), v.toInt())