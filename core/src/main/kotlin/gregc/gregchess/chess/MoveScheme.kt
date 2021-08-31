package gregc.gregchess.chess

import gregc.gregchess.*

fun defaultColor(square: Square) = if (square.piece == null) Floor.MOVE else Floor.CAPTURE

val defaultOrder = with (MoveNameTokenType) { listOf(PIECE_TYPE, UNIQUENESS_COORDINATE, CAPTURE, TARGET, CHECK, CHECKMATE) }

fun jumps(piece: BoardPiece, dirs: Collection<Dir>) =
    dirs.map { piece.pos + it }.filter { it.isValid() }.mapNotNull { piece.square.board[it] }.map {
        Move(piece.info, it.pos, defaultColor(it),
            listOf(piece.pos), listOf(it.pos), emptyList(), listOf(it.pos),
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


class PawnMovement(
    private val canDouble: (PieceInfo) -> Boolean = { !it.hasMoved },
    private val promotions: (PieceInfo) -> List<Piece> = { p -> defaultPromotions.map { it.of(p.side) } }
) : MoveScheme {
    companion object {
        private val defaultPromotions = listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)
        @JvmField
        val EN_PASSANT = GregChessModule.register("en_passant", ChessFlagType(1u))
        private fun ifProm(promotions: Any?, floor: Floor) = if (promotions == null) floor else Floor.SPECIAL
    }

    override fun generate(piece: BoardPiece): List<Move> = buildList {
        fun promotions(pos: Pos) = if (pos.rank in listOf(0, 7)) promotions(piece.info) else null

        val dir = piece.side.dir
        val pos = piece.pos
        val forward = pos + dir
        if (forward.isValid()) {
            add(Move(piece.info, forward, ifProm(promotions(forward), Floor.MOVE),
                listOf(pos), listOf(forward), listOf(forward), listOf(forward),
                emptyList(), emptyList(),
                listOf(PawnOriginTrait(), PromotionTrait(promotions(forward)), TargetTrait(forward), CheckTrait()),
                defaultOrder
            ))
        }
        val forward2 = pos + dir * 2
        if (forward2.isValid() && canDouble(piece.info)) {
            add(Move(piece.info, forward2, ifProm(promotions(forward), Floor.MOVE),
                listOf(pos), listOf(forward2), listOf(forward, forward2), listOf(forward, forward2),
                emptyList(), listOf(PosFlag(forward, ChessFlag(EN_PASSANT))),
                listOf(PawnOriginTrait(), PromotionTrait(promotions(forward2)), TargetTrait(forward2), CheckTrait()),
                defaultOrder
            ))
        }
        for (s in BoardSide.values()) {
            val capture = pos + dir + s.dir
            if (capture.isValid()) {
                add(Move(piece.info, capture, ifProm(promotions(forward), Floor.CAPTURE),
                    listOf(pos), listOf(capture), emptyList(), listOf(capture),
                    emptyList(), emptyList(),
                    listOf(PawnOriginTrait(), PromotionTrait(promotions(capture)),
                        CaptureTrait(capture, true), TargetTrait(capture), CheckTrait()),
                    defaultOrder
                ))
                val enPassant = pos + s.dir
                add(Move(piece.info, capture, ifProm(promotions(forward), Floor.CAPTURE),
                    listOf(pos), listOf(capture), emptyList(), listOf(capture),
                    listOf(Pair(capture, EN_PASSANT)), emptyList(),
                    listOf(PawnOriginTrait(), PromotionTrait(promotions(capture)),
                        CaptureTrait(enPassant, true), TargetTrait(capture),
                        NameTrait(listOf(MoveNameTokenType.EN_PASSANT.mk)), CheckTrait()),
                    defaultOrder + MoveNameTokenType.EN_PASSANT
                ))
            }
        }
    }
}