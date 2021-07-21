package gregc.gregchess.chess

import gregc.gregchess.GregChess
import net.minecraft.util.Identifier

val gregc.gregchess.Identifier.fabric get() = Identifier(namespace, path)

val Piece.block get() = GregChess.PIECE_BLOCKS[this]!!
val Piece.item get() = GregChess.PIECE_ITEMS[this]!!

val Floor.chess get() = ChessboardFloor.valueOf(name)