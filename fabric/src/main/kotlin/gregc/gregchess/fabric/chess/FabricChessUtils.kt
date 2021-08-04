package gregc.gregchess.fabric.chess

import gregc.gregchess.ChessModule
import gregc.gregchess.chess.*
import gregc.gregchess.fabric.FabricGregChessModule
import net.minecraft.util.Identifier

val PieceType.id get() = Identifier(ChessModule[this].namespace, name.lowercase())

val Piece.block get() = FabricGregChessModule.pieceBlocks[this]!!
val Piece.item get() = FabricGregChessModule.pieceItems[this]!!
val Piece.id get() = type.id.let { Identifier(it.namespace, side.name.lowercase() + "_" + it.path) }

fun pieceOfId(id: Identifier): Piece? {
    val side = when (id.path.take(6)) {
        "white_" -> white
        "black_" -> black
        else -> return null
    }
    val type = ChessModule[id.namespace].pieceTypes.find { it.name.lowercase() == id.path.drop(6) } ?: return null
    return Piece(type, side)
}

val Floor.chess get() = ChessboardFloor.valueOf(name)

fun interface ChessInitializer {
    fun onInitializeChess()
}