package gregc.gregchess.chess

import gregc.gregchess.Identifier
import gregc.gregchess.asIdent


data class PieceType(
    val id: Identifier,
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
        val KING = PieceType("king".asIdent(), 'k', ::kingMovement, ::assumeNotMoved, false)
        @JvmField
        val QUEEN = PieceType("queen".asIdent(), 'q', ::queenMovement, ::assumeNotMoved, false)
        @JvmField
        val ROOK = PieceType("rook".asIdent(), 'r', ::rookMovement, ::rookHasMoved, false)
        @JvmField
        val BISHOP = PieceType("bishop".asIdent(), 'b', ::bishopMovement, ::assumeNotMoved, true)
        @JvmField
        val KNIGHT = PieceType("knight".asIdent(), 'n', ::knightMovement, ::assumeNotMoved, true)
        @JvmField
        val PAWN = PieceType("pawn".asIdent(), 'p', pawnMovement(DefaultPawnConfig), ::pawnHasMoved, false)

        fun values() = values

        fun parseFromStandardChar(c: Char): PieceType =
            values.firstOrNull { it.standardChar == c.lowercaseChar() } ?: throw IllegalArgumentException(c.toString())
        fun get(id: Identifier) = values.firstOrNull { it.id == id } ?: throw IllegalArgumentException(id.toString())
    }
}