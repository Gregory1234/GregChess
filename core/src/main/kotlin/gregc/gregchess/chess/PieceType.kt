package gregc.gregchess.chess


data class PieceType(
    val standardName: String,
    val standardChar: Char,
    val moveScheme: (BoardPiece) -> List<MoveCandidate>,
    val hasMoved: (FEN, Pos, Side) -> Boolean,
    val minor: Boolean
) {

    init {
        values += this
    }

    companion object {

        private val values = mutableListOf<PieceType>()

        @Suppress("UNUSED_PARAMETER")
        private fun assumeNotMoved(fen: FEN, p: Pos, s: Side) = false
        private fun rookHasMoved(fen: FEN, p: Pos, s: Side) = p.file !in fen.castlingRights[s]
        @Suppress("UNUSED_PARAMETER")
        private fun pawnHasMoved(fen: FEN, p: Pos, s: Side) = when (s) {
            Side.WHITE -> p.rank != 1
            Side.BLACK -> p.rank != 6
        }
        @JvmField
        val KING = PieceType("King", 'k', ::kingMovement, ::assumeNotMoved, false)
        @JvmField
        val QUEEN = PieceType("Queen", 'q', ::queenMovement, ::assumeNotMoved, false)
        @JvmField
        val ROOK = PieceType("Rook", 'r', ::rookMovement, ::rookHasMoved, false)
        @JvmField
        val BISHOP = PieceType("Bishop", 'b', ::bishopMovement, ::assumeNotMoved, true)
        @JvmField
        val KNIGHT = PieceType("Knight", 'n', ::knightMovement, ::assumeNotMoved, true)
        @JvmField
        val PAWN = PieceType("Pawn", 'p', pawnMovement(DefaultPawnConfig), ::pawnHasMoved, false)

        fun values() = values

        fun parseFromStandardChar(c: Char): PieceType =
            values.firstOrNull { it.standardChar == c.lowercaseChar() } ?: throw IllegalArgumentException(c.toString())
        fun valueOf(n: String) = values.firstOrNull { it.standardName == n } ?: throw IllegalArgumentException(n)
    }
}