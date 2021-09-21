package gregc.gregchess.chess

import gregc.gregchess.*
import kotlinx.serialization.Serializable

@Serializable(with = PieceType.Serializer::class)
class PieceType(
    val char: Char,
    val moveScheme: MoveScheme,
    val hasMoved: (FEN, Pos, Color) -> Boolean,
    val minor: Boolean
) : NameRegistered {

    object Serializer : NameRegisteredSerializer<PieceType>("PieceType", RegistryType.PIECE_TYPE)

    override val key get() = RegistryType.PIECE_TYPE[this]

    override fun toString(): String = "$key@${hashCode().toString(16)}"

    companion object {

        private val assumeNotMoved = { _: FEN, _: Pos, _: Color -> false }
        private val rookHasMoved = { fen: FEN, p: Pos, s: Color -> p.file !in fen.castlingRights[s] }
        private val pawnHasMoved = { _: FEN, p: Pos, s: Color ->
            when (s) {
                Color.WHITE -> p.rank != 1
                Color.BLACK -> p.rank != 6
            }
        }

        private val bishopDirs = rotationsOf(1, 1)
        private val rookDirs = rotationsOf(1, 0)
        private val queenDirs = bishopDirs + rookDirs
        private val knightDirs = rotationsOf(2, 1)

        @JvmField
        val KING = GregChessModule.register("king", PieceType('k', KingMovement, assumeNotMoved, false))
        @JvmField
        val QUEEN = GregChessModule.register("queen", PieceType('q', RayMovement(queenDirs), assumeNotMoved, false))
        @JvmField
        val ROOK = GregChessModule.register("rook", PieceType('r', RayMovement(rookDirs), rookHasMoved, false))
        @JvmField
        val BISHOP = GregChessModule.register("bishop", PieceType('b', RayMovement(bishopDirs), assumeNotMoved, true))
        @JvmField
        val KNIGHT = GregChessModule.register("knight", PieceType('n', JumpMovement(knightDirs), assumeNotMoved, true))
        @JvmField
        val PAWN = GregChessModule.register("pawn", PieceType('p', PawnMovement(), pawnHasMoved, false))

        fun chooseByChar(values: Collection<PieceType>, c: Char): PieceType =
            requireNotNull(values.firstOrNull { it.char == c.lowercaseChar() }) { "None of the pieces have character: '$c'" }
    }
}