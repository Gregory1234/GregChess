package gregc.gregchess.chess

import gregc.gregchess.*
import kotlinx.serialization.Serializable

@Serializable(with = PieceType.Serializer::class)
class PieceType(
    val char: Char,
    val moveScheme: MoveScheme,
    val hasMoved: (FEN, Pos, Side) -> Boolean,
    val minor: Boolean
): NameRegistered {

    object Serializer: NameRegisteredSerializer<PieceType>("PieceType", RegistryType.PIECE_TYPE)

    override val module get() = RegistryType.PIECE_TYPE.getModule(this)
    override val name get() = RegistryType.PIECE_TYPE[this]

    override fun toString(): String = "${module.namespace}:$name@${hashCode().toString(16)}"

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
        val KING = GregChessModule.register("king", PieceType('k', KingMovement, assumeNotMoved, false))
        @JvmField
        val QUEEN = GregChessModule.register("queen", PieceType('q', RayMovement(rotationsOf(1, 0) + rotationsOf(1, 1)), assumeNotMoved, false))
        @JvmField
        val ROOK = GregChessModule.register("rook", PieceType('r', RayMovement(rotationsOf(1, 0)), rookHasMoved, false))
        @JvmField
        val BISHOP = GregChessModule.register("bishop", PieceType('b', RayMovement(rotationsOf(1, 1)), assumeNotMoved, true))
        @JvmField
        val KNIGHT = GregChessModule.register("knight", PieceType('n', JumpMovement(rotationsOf(2, 1)), assumeNotMoved, true))
        @JvmField
        val PAWN = GregChessModule.register("pawn", PieceType('p', PawnMovement(), pawnHasMoved, false))

        fun chooseByChar(values: Collection<PieceType>, c: Char): PieceType =
            values.firstOrNull { it.char == c.lowercaseChar() } ?: throw IllegalArgumentException(c.toString())
    }
}