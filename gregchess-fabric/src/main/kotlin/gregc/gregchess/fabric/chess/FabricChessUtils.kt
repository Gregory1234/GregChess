package gregc.gregchess.fabric.chess

import gregc.gregchess.chess.piece.Piece
import gregc.gregchess.fabric.FabricRegistry
import gregc.gregchess.registry.*
import net.minecraft.util.Identifier

val NameRegistered.id get() = Identifier(module.namespace, name)

val Piece.block get() = FabricRegistry.PIECE_BLOCK[this]
val Piece.item get() = FabricRegistry.PIECE_ITEM[this]

fun interface ChessInitializer {
    fun onInitializeChess()
}