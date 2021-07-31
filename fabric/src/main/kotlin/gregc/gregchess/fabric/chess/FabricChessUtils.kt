package gregc.gregchess.fabric.chess

import gregc.gregchess.GregChessModule
import gregc.gregchess.chess.*
import gregc.gregchess.fabric.FabricGregChessModule
import net.minecraft.util.Identifier

val PieceType.id get() = Identifier(GregChessModule.pieceTypeModule(this).namespace, name.lowercase())

val Piece.block get() = FabricGregChessModule.pieceBlocks[this]!!
val Piece.item get() = FabricGregChessModule.pieceItems[this]!!
val Piece.id get() = type.id.let { Identifier(it.namespace, side.name.lowercase() + "_" + it.path) }

val Floor.chess get() = ChessboardFloor.valueOf(name)

fun interface ChessInitializer {
    fun onInitializeChess()
}