package gregc.gregchess.chess

import gregc.gregchess.ident

val Piece.id get() = ident("${side.standardName.lowercase()}_${type.standardName.lowercase()}")