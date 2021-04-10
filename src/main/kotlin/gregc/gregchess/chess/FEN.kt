package gregc.gregchess.chess

import gregc.gregchess.glog

data class FEN(
    val boardState: String = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR",
    val currentTurn: ChessSide = ChessSide.WHITE,
    val castlingRightsWhite: List<Int> = listOf(0, 7),
    val castlingRightsBlack: List<Int> = listOf(0, 7),
    val enPassantSquare: ChessPosition? = null,
    val halfmoveClock: Int = 0,
    val fullmoveClock: Int = 1
) {

    fun forEachSquare(f: (ChessPosition, Triple<ChessType, ChessSide, Boolean>?) -> Unit) {
        val rows = boardState.split('/')

        rows.reversed().forEachIndexed { ri, r ->
            var i = 0
            r.forEach { c ->
                if (c in '1'..'8') {
                    repeat(c - '0') { j -> f(ChessPosition(i + j, ri), null) }
                    i += c - '0'
                } else {
                    val type = ChessType.parseFromStandardChar(c)
                    val side = if (c.isUpperCase()) ChessSide.WHITE else ChessSide.BLACK
                    val hasMoved = when (type) {
                        ChessType.PAWN -> when (side) {
                            ChessSide.WHITE -> ri != 1
                            ChessSide.BLACK -> ri != 6
                        }
                        ChessType.ROOK -> when (side) {
                            ChessSide.WHITE -> i !in castlingRightsWhite
                            ChessSide.BLACK -> i !in castlingRightsBlack
                        }
                        else -> false
                    }
                    f(ChessPosition(i, ri), Triple(type, side, hasMoved))
                    i++
                }
            }
        }
    }

    override fun toString() = "$boardState ${currentTurn.standardChar} ${
        castlingRightsToString('A', castlingRightsWhite)
    }${
        castlingRightsToString('a', castlingRightsBlack)
    } ${enPassantSquare ?: "-"} $halfmoveClock $fullmoveClock"

    fun toHash() = "$boardState ${currentTurn.standardChar} ${
        castlingRightsToString('A', castlingRightsWhite)
    }${
        castlingRightsToString('a', castlingRightsBlack)
    } ${enPassantSquare ?: "-"}"

    fun isInitial() = this == FEN()

    companion object {
        private fun castlingRightsToString(base: Char, cr: List<Int>) =
            if (cr.all { it in listOf(0, 7) })
                cr.map { if (it == 0) base + ('q' - 'a') else base + ('k' - 'a') }.joinToString("")
                    .reversed()
            else
                cr.map { base + it }.joinToString("")

        private fun parseCastlingRights(s: String) = s.toLowerCase().map {
            when (it) {
                'k' -> 7
                'q' -> 0
                in 'a'..'h' -> it - 'a'
                else -> throw IllegalArgumentException(s)
            }
        }

        fun parseFromString(fen: String): FEN {
            val parts = fen.split(" ")
            if (parts.size != 6) throw IllegalArgumentException(fen)
            if (parts[1].length != 1) throw IllegalArgumentException(fen)
            if (parts[4].toInt() < 0) throw IllegalArgumentException(fen)
            if (parts[5].toInt() <= 0) throw IllegalArgumentException(fen)
            return FEN(
                parts[0],
                ChessSide.parseFromStandardChar(parts[1][0]),
                parseCastlingRights(parts[2].filter { it.isUpperCase() }),
                parseCastlingRights(parts[2].filter { it.isLowerCase() }),
                if (parts[3] == "-") null else ChessPosition.parseFromString(parts[3]),
                parts[4].toInt(),
                parts[5].toInt()
            )
        }

        fun generateChess960(): FEN {
            val types = MutableList<Char?>(8) { null }
            types[(0..7).filter { it % 2 == 0 }.random()] = ChessType.BISHOP.standardChar
            types[(0..7).filter { it % 2 == 1 }.random()] = ChessType.BISHOP.standardChar
            types[(0..7).filter { types[it] == null }.random()] = ChessType.KNIGHT.standardChar
            types[(0..7).filter { types[it] == null }.random()] = ChessType.KNIGHT.standardChar
            types[(0..7).filter { types[it] == null }.random()] = ChessType.QUEEN.standardChar
            val r1 = types.indexOf(null)
            types[r1] = ChessType.ROOK.standardChar
            types[types.indexOf(null)] = ChessType.KING.standardChar
            val r2 = types.indexOf(null)
            types[r2] = ChessType.ROOK.standardChar
            val row = String(types.mapNotNull { it }.toCharArray())
            val pawns = ChessType.PAWN.standardChar.toString().repeat(8)
            return FEN(
                "$row/$pawns/8/8/8/8/${pawns.toUpperCase()}/${row.toUpperCase()}",
                castlingRightsWhite = listOf(r1, r2),
                castlingRightsBlack = listOf(r1, r2)
            )
        }
    }
}