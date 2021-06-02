package gregc.gregchess.chess

import gregc.gregchess.component6

data class FEN(
    val boardState: BoardState = BoardState("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"),
    val currentTurn: Side = Side.WHITE,
    val castlingRights: BySides<List<Int>> = BySides(listOf(0, 7)),
    val enPassantSquare: Pos? = null,
    val halfmoveClock: UInt = 0u,
    val fullmoveClock: UInt = 1u,
    val chess960: Boolean = false
) {
    @JvmInline
    value class BoardState(private val state: String){
        companion object {
            fun fromPieces(pieces: Map<Pos, PieceInfo>): BoardState {
                val rows = List(8) { ri ->
                    var e = 0
                    buildString {
                        (0 until 8).forEach { i ->
                            val p = pieces[Pos(i, ri)]
                            if (p == null)
                                e++
                            else {
                                if (e != 0)
                                    append(e.digitToChar())
                                e = 0
                                append(p.standardChar)
                            }
                        }
                        if (e != 0)
                            append(e.digitToChar())
                    }
                }
                return BoardState(rows.joinToString("/"))
            }
        }

        init {
            if (state.count {it == '/'} != 7)
                throw IllegalArgumentException(state)
        }
        fun forEachIndexed(block: (Pos, Char?) -> Unit) {
            val rows = state.split('/')
            rows.forEachIndexed { ri, r ->
                var i = 0
                r.forEach { c ->
                    if (c in '1'..'8') {
                        repeat(c.digitToInt()) { j -> block(Pos(i + j, 7-ri), null) }
                        i += c.digitToInt()
                    } else {
                        block(Pos(i, 7-ri), c)
                        i++
                    }
                }
            }
        }
        fun mapIndexed(block: (Int, String) -> String) =
            BoardState(state.split('/').mapIndexed(block).joinToString("/"))
    }

    private fun Char.toPiece(p: Pos): PieceInfo {
        val type = PieceType.parseFromStandardChar(this)
        val side = if (isUpperCase()) Side.WHITE else Side.BLACK
        val hasMoved = when (type) {
            PieceType.PAWN -> when (side) {
                Side.WHITE -> p.rank != 1
                Side.BLACK -> p.rank != 6
            }
            PieceType.ROOK -> p.file !in castlingRights[side]
            else -> false
        }
        return PieceInfo(p, Piece(type, side), hasMoved)
    }

    fun forEachSquare(f: (PieceInfo) -> Unit)
        = boardState.forEachIndexed { p, c -> if (c != null) f(c.toPiece(p))}

    override fun toString() = buildString {
        append(boardState)
        append(" ")
        append(currentTurn.standardChar)
        append(" ")
        append(castlingRightsToString(chess960, 'A', castlingRights.white))
        append(castlingRightsToString(chess960, 'a', castlingRights.black))
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
        append(castlingRightsToString(chess960, 'A', castlingRights.white))
        append(castlingRightsToString(chess960, 'a', castlingRights.black))
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
                BoardState(board),
                Side.parseFromStandardChar(turn[0]),
                BySides(
                    parseCastlingRights(board.split("/").first(), castling.filter { it.isUpperCase() }),
                    parseCastlingRights(board.split("/").last(), castling.filter { it.isLowerCase() })
                ),
                if (enPassant == "-") null else Pos.parseFromString(enPassant),
                halfmove.toUInt(),
                fullmove.toUInt(),
                detectChess960(board, castling)
            )
        }

        fun generateChess960(): FEN {
            val types = MutableList<Char?>(8) { null }
            types[(0..7).filter { it % 2 == 0 }.random()] = PieceType.BISHOP.standardChar
            types[(0..7).filter { it % 2 == 1 }.random()] = PieceType.BISHOP.standardChar
            types[(0..7).filter { types[it] == null }.random()] = PieceType.KNIGHT.standardChar
            types[(0..7).filter { types[it] == null }.random()] = PieceType.KNIGHT.standardChar
            types[(0..7).filter { types[it] == null }.random()] = PieceType.QUEEN.standardChar
            val r1 = types.indexOf(null)
            types[r1] = PieceType.ROOK.standardChar
            types[types.indexOf(null)] = PieceType.KING.standardChar
            val r2 = types.indexOf(null)
            types[r2] = PieceType.ROOK.standardChar
            val row = String(types.mapNotNull { it }.toCharArray())
            val pawns = PieceType.PAWN.standardChar.toString().repeat(8)
            return FEN(
                BoardState("$row/$pawns/8/8/8/8/${pawns.uppercase()}/${row.uppercase()}"),
                castlingRights = BySides(listOf(r1, r2)),
                chess960 = true
            )
        }
    }
}