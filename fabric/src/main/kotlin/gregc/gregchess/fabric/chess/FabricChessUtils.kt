package gregc.gregchess.fabric.chess

import gregc.gregchess.*
import gregc.gregchess.chess.Floor
import gregc.gregchess.chess.Piece
import gregc.gregchess.fabric.FabricRegistryTypes
import net.minecraft.util.Identifier

val NameRegistered.id get() = Identifier(module.namespace, name)

val Piece.block get() = FabricRegistryTypes.PIECE_BLOCK[type.module, this]
val Piece.item get() = FabricRegistryTypes.PIECE_ITEM[type.module, this]

val Floor.chess get() = ChessboardFloor.valueOf(name)

fun interface ChessInitializer {
    fun onInitializeChess()
}