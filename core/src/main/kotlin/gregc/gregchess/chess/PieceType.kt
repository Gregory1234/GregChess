package gregc.gregchess.chess


class PieceType(
    val name: String,
    val standardChar: Char,
    val moveScheme: (BoardPiece) -> List<MoveCandidate>,
    val hasMoved: (FEN, Pos, Side) -> Boolean,
    val minor: Boolean
) {

    override fun toString(): String = "$name(${hashCode().toString(16)})"

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
        val KING = PieceType("KING",'k', ::kingMovement, ::assumeNotMoved, false)
        @JvmField
        val QUEEN = PieceType("QUEEN",'q', ::queenMovement, ::assumeNotMoved, false)
        @JvmField
        val ROOK = PieceType("ROOK",'r', ::rookMovement, ::rookHasMoved, false)
        @JvmField
        val BISHOP = PieceType("BISHOP",'b', ::bishopMovement, ::assumeNotMoved, true)
        @JvmField
        val KNIGHT = PieceType("KNIGHT",'n', ::knightMovement, ::assumeNotMoved, true)
        @JvmField
        val PAWN = PieceType("PAWN",'p', pawnMovement(DefaultPawnConfig), ::pawnHasMoved, false)

        fun parseFromStandardChar(values: Collection<PieceType>, c: Char): PieceType =
            values.firstOrNull { it.standardChar == c.lowercaseChar() } ?: throw IllegalArgumentException(c.toString())
    }
}