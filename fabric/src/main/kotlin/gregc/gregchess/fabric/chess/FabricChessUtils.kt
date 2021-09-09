package gregc.gregchess.fabric.chess

import gregc.gregchess.*
import gregc.gregchess.chess.*
import gregc.gregchess.fabric.FabricRegistryTypes
import net.minecraft.util.Identifier

val PieceType.id get() = Identifier(module.namespace, name)

val Piece.block get() = FabricRegistryTypes.PIECE_BLOCK[type.module, type][side]
val Piece.item get() = FabricRegistryTypes.PIECE_ITEM[type.module, type][side]
val Piece.id get() = type.id.let { Identifier(it.namespace, side.name.lowercase() + "_" + it.path) }

fun pieceOfId(id: Identifier): Piece? {
    val side = when (id.path.take(6)) {
        "white_" -> white
        "black_" -> black
        else -> return null
    }
    val type = RegistryType.Companion.PIECE_TYPE[id.namespace, id.path.drop(6)]
    return Piece(type, side)
}

val Floor.chess get() = ChessboardFloor.valueOf(name)

fun interface ChessInitializer {
    fun onInitializeChess()
}