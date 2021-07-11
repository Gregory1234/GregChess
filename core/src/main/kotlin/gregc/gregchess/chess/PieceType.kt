package gregc.gregchess.chess


data class PieceType(
    val standardName: String,
    val standardChar: Char,
    val moveScheme: (BoardPiece) -> List<MoveCandidate>,
    val minor: Boolean
) {
    init {
        values += this
    }

    companion object {

        private val values = mutableListOf<PieceType>()

        fun values() = values.toTypedArray()

        val KING = PieceType("King", 'k', ::kingMovement, false)
        val QUEEN = PieceType("Queen", 'q', ::queenMovement, false)
        val ROOK = PieceType("Rook", 'r', ::rookMovement, false)
        val BISHOP = PieceType("Bishop", 'b', ::bishopMovement, true)
        val KNIGHT = PieceType("Knight", 'n', ::knightMovement, true)
        val PAWN = PieceType("Pawn", 'p', ::pawnMovement, false)

        fun parseFromStandardChar(c: Char): PieceType =
            values.firstOrNull { it.standardChar == c.lowercaseChar() } ?: throw IllegalArgumentException(c.toString())
        fun valueOf(n: String) = values.firstOrNull { it.standardName == n } ?: throw IllegalArgumentException(n)
    }
}