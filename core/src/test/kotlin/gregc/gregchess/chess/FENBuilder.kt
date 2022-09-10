package gregc.gregchess.chess

import gregc.gregchess.*
import gregc.gregchess.board.FEN
import gregc.gregchess.piece.Piece

class FENBuilder {
    private val pieces = mutableMapOf<Pos, Piece>()
    var currentColor: Color = Color.WHITE
    var castlingRights: ByColor<List<Int>> = byColor(emptyList())
    var enPassantSquare: Pos? = null
    var halfmoveClock: Int = 0
    var fullmoveCounter: Int = 1

    val boardState get() = FEN.boardStateFromPieces(pieces)

    operator fun set(pos: Pos, piece: Piece) {
        pieces[pos] = piece
    }

    fun build(): FEN = FEN(boardState, currentColor, castlingRights, enPassantSquare, halfmoveClock, fullmoveCounter)
}

fun fen(builder: FENBuilder.() -> Unit) = FENBuilder().apply(builder).build()