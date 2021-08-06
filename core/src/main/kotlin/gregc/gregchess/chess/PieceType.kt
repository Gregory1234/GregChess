package gregc.gregchess.chess

import gregc.gregchess.*


class PieceType(
    val name: String,
    val char: Char,
    val moveScheme: MoveScheme,
    val hasMoved: (FEN, Pos, Side) -> Boolean,
    val minor: Boolean
) {

    init {
        require(name.isValidName())
    }

    override fun toString(): String = "$name@${hashCode().toString(16)}"

    companion object {

        private val assumeNotMoved = { _: FEN, _: Pos, _: Side -> false }
        private val rookHasMoved = { fen: FEN, p: Pos, s: Side -> p.file !in fen.castlingRights[s] }
        private val pawnHasMoved = { _: FEN, p: Pos, s: Side ->
            when (s) {
                Side.WHITE -> p.rank != 1
                Side.BLACK -> p.rank != 6
            }
        }

        @JvmField
        val KING = GregChessModule.register(PieceType("KING", 'k', KingMovement, assumeNotMoved, false))
        @JvmField
        val QUEEN = GregChessModule.register(PieceType("QUEEN", 'q', RayMovement(rotationsOf(1, 0) + rotationsOf(1, 1)), assumeNotMoved, false))
        @JvmField
        val ROOK = GregChessModule.register(PieceType("ROOK", 'r', RayMovement(rotationsOf(1, 0)), rookHasMoved, false))
        @JvmField
        val BISHOP = GregChessModule.register(PieceType("BISHOP", 'b', RayMovement(rotationsOf(1, 1)), assumeNotMoved, true))
        @JvmField
        val KNIGHT = GregChessModule.register(PieceType("KNIGHT", 'n', JumpMovement(rotationsOf(2, 1)), assumeNotMoved, true))
        @JvmField
        val PAWN = GregChessModule.register(PieceType("PAWN", 'p', PawnMovement(), pawnHasMoved, false))

        fun chooseByChar(values: Collection<PieceType>, c: Char): PieceType =
            values.firstOrNull { it.char == c.lowercaseChar() } ?: throw IllegalArgumentException(c.toString())
    }
}