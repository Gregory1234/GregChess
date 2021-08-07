package gregc.gregchess.fabric.chess

import gregc.gregchess.*
import gregc.gregchess.chess.*
import gregc.gregchess.fabric.pieceBlocks
import gregc.gregchess.fabric.pieceItems
import net.minecraft.util.Identifier

val PieceType.module get() = RegistryType.PIECE_TYPE.getModule(this)
val PieceType.id get() = Identifier(module.namespace, name.lowercase())

val Piece.block get() = type.module.pieceBlocks[type][side]
val Piece.item get() = type.module.pieceItems[type][side]
val Piece.id get() = type.id.let { Identifier(it.namespace, side.name.lowercase() + "_" + it.path) }

fun pieceOfId(id: Identifier): Piece? {
    val side = when (id.path.take(6)) {
        "white_" -> white
        "black_" -> black
        else -> return null
    }
    val type = ChessModule[id.namespace].pieceTypes[id.path.drop(6)]
    return Piece(type, side)
}

val Floor.chess get() = ChessboardFloor.valueOf(name)

fun interface ChessInitializer {
    fun onInitializeChess()
}