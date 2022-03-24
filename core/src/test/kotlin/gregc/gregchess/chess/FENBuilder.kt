package gregc.gregchess.chess

import gregc.gregchess.chess.piece.Piece

class FENBuilder {
    private val pieces = mutableMapOf<Pos, Piece>()
    var currentTurn: Color = Color.WHITE
    var castlingRights: ByColor<List<Int>> = byColor(emptyList())
    var enPassantSquare: Pos? = null
    var halfmoveClock: Int = 0
    var fullmoveCounter: Int = 1
    var chess960: Boolean = false

    val boardState get() = FEN.boardStateFromPieces(pieces)

    operator fun set(pos: Pos, piece: Piece) {
        pieces[pos] = piece
    }

    fun build(): FEN = FEN(boardState, currentTurn, castlingRights, enPassantSquare, halfmoveClock, fullmoveCounter, chess960)
}

fun fen(builder: FENBuilder.() -> Unit) = FENBuilder().apply(builder).build()