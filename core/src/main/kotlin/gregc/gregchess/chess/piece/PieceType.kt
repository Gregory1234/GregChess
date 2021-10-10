package gregc.gregchess.chess.piece

import gregc.gregchess.*
import gregc.gregchess.chess.*
import gregc.gregchess.chess.move.*
import gregc.gregchess.registry.*
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

        private fun register(id: String, type: PieceType) = GregChessModule.register(id, type)

        @JvmField
        val KING = register("king", PieceType('k', KingMovement, assumeNotMoved, false))
        @JvmField
        val QUEEN = register("queen", PieceType('q', RayMovement(queenDirs), assumeNotMoved, false))
        @JvmField
        val ROOK = register("rook", PieceType('r', RayMovement(rookDirs), rookHasMoved, false))
        @JvmField
        val BISHOP = register("bishop", PieceType('b', RayMovement(bishopDirs), assumeNotMoved, true))
        @JvmField
        val KNIGHT = register("knight", PieceType('n', JumpMovement(knightDirs), assumeNotMoved, true))

        private val pawnMoveScheme = PromotionMovement(PawnMovement(), listOf(QUEEN, ROOK, BISHOP, KNIGHT))

        @JvmField
        val PAWN = register("pawn", PieceType('p', pawnMoveScheme, pawnHasMoved, false))

        fun chooseByChar(values: Collection<PieceType>, c: Char): PieceType =
            requireNotNull(values.firstOrNull { it.char == c.lowercaseChar() }) { "None of the pieces have character: '$c'" }
    }
}