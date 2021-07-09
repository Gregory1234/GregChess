package gregc.gregchess.chess

import gregc.gregchess.snakeToPascal


enum class PieceType(
    val standardChar: Char,
    val moveScheme: (BoardPiece) -> List<MoveCandidate>,
    val minor: Boolean
) {
    KING('k', ::kingMovement, false),
    QUEEN('q', ::queenMovement, false),
    ROOK('r', ::rookMovement, false),
    BISHOP('b', ::bishopMovement, true),
    KNIGHT('n', ::knightMovement, true),
    PAWN('p', ::pawnMovement, false);

    companion object {

        fun parseFromStandardChar(c: Char): PieceType =
            values().firstOrNull { it.standardChar == c.lowercaseChar() }
                ?: throw IllegalArgumentException(c.toString())
    }

    val standardName: String = name.snakeToPascal()
}