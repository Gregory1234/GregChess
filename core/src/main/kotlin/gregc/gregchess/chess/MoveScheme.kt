package gregc.gregchess.chess

import gregc.gregchess.GregChessModule
import gregc.gregchess.register

fun defaultColor(square: Square) = if (square.piece == null) Floor.MOVE else Floor.CAPTURE

val defaultOrder = with (MoveNameTokenType) { listOf(PIECE_TYPE, UNIQUENESS_COORDINATE, TARGET, CHECK, CHECKMATE) }

fun jumps(piece: BoardPiece, dirs: Collection<Dir>) =
    dirs.map { piece.pos + it }.filter { it.isValid() }.mapNotNull { piece.square.board[it] }.map {
        Move(piece.info, it.pos, defaultColor(it),
            listOf(piece.pos), listOf(it.pos), emptyList(), emptyList(),
            emptyList(), emptyList(),
            listOf(PieceOriginTrait(), CaptureTrait(it.pos), TargetTrait(it.pos), CheckTrait()),
            defaultOrder
        )
    }

fun rays(piece: BoardPiece, dirs: Collection<Dir>) =
    dirs.flatMap { dir ->
        PosSteps(piece.pos + dir, dir).mapIndexedNotNull { index, pos ->
            piece.square.board[pos]?.let {
                Move(piece.info, it.pos, defaultColor(it),
                    listOf(piece.pos), listOf(it.pos),
                    PosSteps(piece.pos + dir, dir, index), PosSteps(piece.pos + dir, dir, index+1),
                    emptyList(), emptyList(),
                    listOf(PieceOriginTrait(), CaptureTrait(it.pos), TargetTrait(it.pos), CheckTrait()),
                    defaultOrder
                )
            }
        }
    }


fun interface MoveScheme {
    fun generate(piece: BoardPiece): List<Move>
}

class JumpMovement(private val dirs: Collection<Dir>) : MoveScheme {
    override fun generate(piece: BoardPiece): List<Move> = jumps(piece, dirs)
}

class RayMovement(private val dirs: Collection<Dir>) : MoveScheme {
    override fun generate(piece: BoardPiece): List<Move> = rays(piece, dirs)
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