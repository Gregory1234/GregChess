package gregc.gregchess.chess

import gregc.gregchess.GregChessModule
import gregc.gregchess.register

fun defaultColor(square: Square) = if (square.piece == null) Floor.MOVE else Floor.CAPTURE

/*fun jumps(piece: BoardPiece, dirs: Collection<Dir>) =
    dirs.map { piece.pos + it }.filter { it.isValid() }.mapNotNull { piece.square.board[it] }.map {
        MoveCandidate(piece, it, defaultColor(it), emptyList())
    }
*/

fun interface MoveScheme {
    fun generate(piece: BoardPiece): List<Move>
}

class JumpMovement(private val dirs: Collection<Dir>) : MoveScheme {
    override fun generate(piece: BoardPiece): List<Move> = emptyList()
}

class RayMovement(private val dirs: Collection<Dir>) : MoveScheme {
    override fun generate(piece: BoardPiece): List<Move> = emptyList()
}

object KingMovement : MoveScheme {

    override fun generate(piece: BoardPiece): List<Move> {
        return emptyList()
    }

}

open class PawnMovementConfig {
    open fun canDouble(piece: PieceInfo): Boolean = !piece.hasMoved
    open fun promotions(piece: PieceInfo): List<Piece> =
        listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT).map { it.of(piece.side) }
}


class PawnMovement(private val config: PawnMovementConfig = PawnMovementConfig()) : MoveScheme {
    companion object {
        @JvmField
        val EN_PASSANT = GregChessModule.register("en_passant", ChessFlagType(1u))
        private fun ifProm(promotions: Any?, floor: Floor) = if (promotions == null) floor else Floor.SPECIAL
    }

    override fun generate(piece: BoardPiece): List<Move> = emptyList()
}