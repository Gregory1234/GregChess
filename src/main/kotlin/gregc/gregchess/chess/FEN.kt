package gregc.gregchess.chess

import gregc.gregchess.component6

data class FEN(
    val boardState: String = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR",
    val currentTurn: ChessSide = ChessSide.WHITE,
    val castlingRightsWhite: List<Int> = listOf(0, 7),
    val castlingRightsBlack: List<Int> = listOf(0, 7),
    val enPassantSquare: ChessPosition? = null,
    val halfmoveClock: Int = 0,
    val fullmoveClock: Int = 1,
    val chess960: Boolean = false
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

    override fun toString() = buildString {
        append(boardState)
        append(" ")
        append(currentTurn.standardChar)
        append(" ")
        append(castlingRightsToString(chess960, 'A', castlingRightsWhite))
        append(castlingRightsToString(chess960, 'a', castlingRightsBlack))
        append(enPassantSquare ?: "-")
        append(" ")
        append(halfmoveClock)
        append(" ")
        append(fullmoveClock)
    }

    fun toHash() = buildString {
        append(boardState)
        append(" ")
        append(currentTurn.standardChar)
        append(" ")
        append(castlingRightsToString(chess960, 'A', castlingRightsWhite))
        append(castlingRightsToString(chess960, 'a', castlingRightsBlack))
        append(enPassantSquare ?: "-")
    }

    fun isInitial() = this == FEN()

    fun hashed(): Int = toHash().hashCode()

    companion object {
        private fun castlingRightsToString(chess960: Boolean, base: Char, cr: List<Int>) =
            cr.map { base + if (chess960) (if (it < 4) 'q' else 'a') - 'a' else it }.sorted()
                .joinToString("")

        private fun parseCastlingRights(r: String, s: String) = s.lowercase().map { c ->
            when (c) {
                'k' -> r.indexOfLast { it.lowercaseChar() == 'r' }
                'q' -> r.indexOfFirst { it.lowercaseChar() == 'r' }
                in 'a'..'h' -> c - 'a'
                else -> throw IllegalArgumentException(s)
            }
        }

        private fun detectChess960(board: String, castling: String): Boolean {
            if (castling.any { it !in "KQkq" })
                return true
            val whiteRow = board.split("/").last()
            val blackRow = board.split("/").first()
            if ("KQ".any { it in castling } && whiteRow.takeWhile { it != 'K' }
                    .sumOf { if (it.isDigit()) it.digitToInt() else 1 } != 4) return true
            if ("kq".any { it in castling } && blackRow.takeWhile { it != 'k' }
                    .sumOf { if (it.isDigit()) it.digitToInt() else 1 } != 4) return true
            castling.forEach {
                when (it) {
                    'K' -> if (!whiteRow.endsWith('R')) return true
                    'Q' -> if (!whiteRow.startsWith('R')) return true
                    'k' -> if (!blackRow.endsWith('r')) return true
                    'q' -> if (!blackRow.startsWith('r')) return true
                }
            }
            return false
        }

        fun parseFromString(fen: String): FEN {
            val parts = fen.split(" ")
            if (parts.size != 6) throw IllegalArgumentException(fen)
            val (board, turn, castling, enPassant, halfmove, fullmove) = parts
            if (turn.length != 1) throw IllegalArgumentException(fen)
            if (halfmove.toInt() < 0) throw IllegalArgumentException(fen)
            if (fullmove.toInt() <= 0) throw IllegalArgumentException(fen)
            return FEN(
                board,
                ChessSide.parseFromStandardChar(turn[0]),
                parseCastlingRights(board.split("/").first(), castling.filter { it.isUpperCase() }),
                parseCastlingRights(board.split("/").last(), castling.filter { it.isLowerCase() }),
                if (enPassant == "-") null else ChessPosition.parseFromString(enPassant),
                halfmove.toInt(),
                fullmove.toInt(),
                detectChess960(board, castling)
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
                "$row/$pawns/8/8/8/8/${pawns.uppercase()}/${row.uppercase()}",
                castlingRightsWhite = listOf(r1, r2),
                castlingRightsBlack = listOf(r1, r2),
                chess960 = true
            )
        }
    }
}