package gregc.gregchess.chess


class PieceType(
    val standardChar: Char,
    val moveScheme: (BoardPiece) -> List<MoveCandidate>,
    val hasMoved: (FEN, Pos, Side) -> Boolean,
    val minor: Boolean
) {

    companion object {

        @Suppress("UNUSED_PARAMETER")
        private fun assumeNotMoved(fen: FEN, p: Pos, s: Side) = false
        private fun rookHasMoved(fen: FEN, p: Pos, s: Side) = p.file !in fen.castlingRights[s]
        @Suppress("UNUSED_PARAMETER")
        private fun pawnHasMoved(fen: FEN, p: Pos, s: Side) = when (s) {
            Side.WHITE -> p.rank != 1
            Side.BLACK -> p.rank != 6
        }
        @JvmField
        val KING = PieceType('k', ::kingMovement, ::assumeNotMoved, false)
        @JvmField
        val QUEEN = PieceType('q', ::queenMovement, ::assumeNotMoved, false)
        @JvmField
        val ROOK = PieceType('r', ::rookMovement, ::rookHasMoved, false)
        @JvmField
        val BISHOP = PieceType('b', ::bishopMovement, ::assumeNotMoved, true)
        @JvmField
        val KNIGHT = PieceType('n', ::knightMovement, ::assumeNotMoved, true)
        @JvmField
        val PAWN = PieceType('p', pawnMovement(DefaultPawnConfig), ::pawnHasMoved, false)

        fun parseFromStandardChar(values: Collection<PieceType>, c: Char): PieceType =
            values.firstOrNull { it.standardChar == c.lowercaseChar() } ?: throw IllegalArgumentException(c.toString())
    }
}