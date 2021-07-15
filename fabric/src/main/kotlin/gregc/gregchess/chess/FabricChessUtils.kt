package gregc.gregchess.chess

import gregc.gregchess.GregChess
import gregc.gregchess.ident

val Piece.id get() = ident("${side.standardName.lowercase()}_${type.standardName.lowercase()}")
val Piece.block get() = GregChess.PIECE_BLOCKS[this]!!
val Piece.item get() = GregChess.PIECE_ITEMS[this]!!

val Floor.chess get() = ChessboardFloor.valueOf(name)