package gregc.gregchess.chess

import gregc.gregchess.chess.piece.*
import gregc.gregchess.chess.variant.ChessVariant
import gregc.gregchess.component6
import kotlinx.serialization.Serializable

@Serializable
data class FEN(
    val boardState: String = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR",
    val currentTurn: Color = Color.WHITE,
    val castlingRights: ByColor<List<Int>> = byColor(listOf(0, 7)),
    val enPassantSquare: Pos? = null,
    val halfmoveClock: UInt = 0u,
    val fullmoveCounter: UInt = 1u,
    val chess960: Boolean = false
) {

    class FENFormatException(val fen: String, cause: Throwable? = null) : IllegalArgumentException(fen, cause)

    private fun Char.toPiece(variant: ChessVariant, p: Pos): BoardPiece {
        val type = PieceType.chooseByChar(variant.pieceTypes, this)
        val color = if (isUpperCase()) Color.WHITE else Color.BLACK
        val piece = type.of(color)
        val hasMoved = variant.startingPieceHasMoved(this@FEN, p, piece)
        return BoardPiece(p, piece, hasMoved)
    }

    fun forEachSquare(variant: ChessVariant, f: (BoardPiece) -> Unit) =
        boardState.split("/").forEachIndexed { rowIndex, row ->
            var file = 0
            row.forEach { char ->
                when (char) {
                    in '1'..'8' -> file += char.digitToInt()
                    else -> {
                        val pos = Pos(file, 7 - rowIndex)
                        f(char.toPiece(variant, pos))
                        file++
                    }
                }
            }
        }

    fun toPieces(variant: ChessVariant): Map<Pos, BoardPiece> = buildMap {
        forEachSquare(variant) {
            set(it.pos, it)
        }
    }

    override fun toString() = buildString {
        append(boardState)
        append(" ")
        append(currentTurn.char)
        append(" ")
        append(castlingRightsToString(chess960, 'A', castlingRights.white))
        append(castlingRightsToString(chess960, 'a', castlingRights.black))
        append(" ")
        append(enPassantSquare ?: "-")
        append(" ")
        append(halfmoveClock)
        append(" ")
        append(fullmoveCounter)
    }

    fun toHash() = buildString {
        append(boardState)
        append(" ")
        append(currentTurn.char)
        append(" ")
        append(castlingRightsToString(chess960, 'A', castlingRights.white))
        append(castlingRightsToString(chess960, 'a', castlingRights.black))
        append(enPassantSquare ?: "-")
    }

    fun isInitial() = this == FEN()

    fun hashed(): Int = toHash().hashCode()

    companion object {
        private fun castlingRightsToString(chess960: Boolean, base: Char, cr: List<Int>) =
            cr.map { base + if (!chess960) (if (it < 4) 'q' else 'k') - 'a' else it }.sorted()
                .joinToString("")

        private fun parseCastlingRights(r: String, s: String) = s.lowercase().map { c ->
            when (c) {
                'k' -> r.indexOfLast { it.lowercaseChar() == 'r' }
                'q' -> r.indexOfFirst { it.lowercaseChar() == 'r' }
                in 'a'..'h' -> c - 'a'
                else -> throw IllegalArgumentException("'$s' is not a valid castling ability character")
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
            for (c in castling) {
                when (c) {
                    'K' -> if (!whiteRow.endsWith('R')) return true
                    'Q' -> if (!whiteRow.startsWith('R')) return true
                    'k' -> if (!blackRow.endsWith('r')) return true
                    'q' -> if (!blackRow.startsWith('r')) return true
                }
            }
            return false
        }

        fun parseFromString(fen: String): FEN = try {
            val parts = fen.split(" ")
            require(parts.size == 6) { "Wrong number of parts in FEN, expected 6: \"$fen\""}
            val (board, turn, castling, enPassant, halfmove, fullmove) = parts
            require(turn.length == 1) { "\"$turn\" is not valid color" }
            require(castling == "-" || castling.all { it.isUpperCase() || it.isLowerCase() }) { "Bad castling ability string: $castling" }
            requireNotNull(halfmove.toIntOrNull()) { "Halfmove clock has to be an integer: $halfmove" }
            require(halfmove.toInt() >= 0) { "Halfmove clock can't be negative: $halfmove" }
            requireNotNull(fullmove.toIntOrNull()) { "Fullmove clock has to be an integer: $fullmove" }
            require(fullmove.toInt() >= 0) { "Fullmove counter has to be positive: $fullmove" }
            FEN(
                board,
                Color.parseFromChar(turn[0]),
                byColor(
                    parseCastlingRights(board.split("/").first(), castling.filter { it.isUpperCase() }),
                    parseCastlingRights(board.split("/").last(), castling.filter { it.isLowerCase() })
                ),
                if (enPassant == "-") null else Pos.parseFromString(enPassant),
                halfmove.toUInt(),
                fullmove.toUInt(),
                detectChess960(board, castling)
            )
        } catch (e : IllegalArgumentException) {
            throw FENFormatException(fen, e)
        }

        fun generateChess960(): FEN {
            val types = MutableList<Char?>(8) { null }
            types[(0..7).filter { it % 2 == 0 }.random()] = PieceType.BISHOP.char
            types[(0..7).filter { it % 2 == 1 }.random()] = PieceType.BISHOP.char
            types[(0..7).filter { types[it] == null }.random()] = PieceType.KNIGHT.char
            types[(0..7).filter { types[it] == null }.random()] = PieceType.KNIGHT.char
            types[(0..7).filter { types[it] == null }.random()] = PieceType.QUEEN.char
            val r1 = types.indexOf(null)
            types[r1] = PieceType.ROOK.char
            types[types.indexOf(null)] = PieceType.KING.char
            val r2 = types.indexOf(null)
            types[r2] = PieceType.ROOK.char
            val row = String(types.mapNotNull { it }.toCharArray())
            val pawns = PieceType.PAWN.char.toString().repeat(8)
            return FEN(
                "$row/$pawns/8/8/8/8/${pawns.uppercase()}/${row.uppercase()}",
                castlingRights = byColor(listOf(r1, r2)),
                chess960 = true
            )
        }

        fun boardStateFromPieces(pieces: Map<Pos, Piece>): String = buildString {
            for (rank in 7 downTo 0) {
                var space = 0
                fun commit() {
                    if (space != 0)
                        append(space.digitToChar())
                    space = 0
                }

                for (file in 0..7) {
                    val pos = Pos(file, rank)
                    val piece = pieces[pos]
                    if (piece != null) {
                        commit()
                        append(piece.char)
                    } else {
                        space++
                    }
                }
                commit()
                if (rank != 0)
                    append('/')
            }
        }

        fun fromPieces(pieces: Map<Pos, Piece>, currentTurn: Color = Color.WHITE): FEN {
            val state = boardStateFromPieces(pieces)
            val rows = state.split("/")
            val castling = byColor {
                val row = rows[byColor(0,7)[it]]
                val rook = byColor('R','r')[it]
                val first = row.indexOfFirst { p -> p == rook }
                val last = row.indexOfLast { p -> p == rook }
                when (first) {
                    -1 -> emptyList()
                    last -> listOf(first)
                    else -> listOf(first, last)
                }
            }
            return FEN(state, currentTurn, castling)
        }
    }
}