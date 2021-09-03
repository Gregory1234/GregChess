package gregc.gregchess.chess

import gregc.gregchess.*

fun defaultColor(square: Square) = if (square.piece == null) Floor.MOVE else Floor.CAPTURE

val defaultOrder = with (MoveNameTokenType) { NameOrder(listOf(PIECE_TYPE, UNIQUENESS_COORDINATE, CAPTURE, TARGET, CHECK, CHECKMATE)) }

fun jumps(piece: BoardPiece, dirs: Collection<Dir>) =
    dirs.map { piece.pos + it }.filter { it.isValid() }.mapNotNull { piece.square.board[it] }.map {
        Move(piece.info, it.pos, defaultColor(it),
            setOf(piece.pos), setOf(it.pos), emptySet(), setOf(it.pos),
            emptySet(), emptySet(),
            listOf(PieceOriginTrait(), CaptureTrait(it.pos), TargetTrait(it.pos), DefaultHalfmoveClockTrait(), CheckTrait()),
            defaultOrder,
        )
    }

fun rays(piece: BoardPiece, dirs: Collection<Dir>) =
    dirs.flatMap { dir ->
        PosSteps(piece.pos + dir, dir).mapIndexedNotNull { index, pos ->
            piece.square.board[pos]?.let {
                Move(piece.info, it.pos, defaultColor(it),
                    setOf(piece.pos), setOf(it.pos),
                    PosSteps(piece.pos + dir, dir, index).toSet(), PosSteps(piece.pos + dir, dir, index+1).toSet(),
                    emptySet(), emptySet(),
                    listOf(PieceOriginTrait(), CaptureTrait(it.pos), TargetTrait(it.pos), DefaultHalfmoveClockTrait(), CheckTrait()),
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

    private val castlesOrder = NameOrder(listOf(MoveNameTokenType.CASTLE, MoveNameTokenType.CHECK, MoveNameTokenType.CHECKMATE))

    private fun castles(piece: BoardPiece, rook: BoardPiece, side: BoardSide, display: Pos, pieceTarget: Pos, rookTarget: Pos) = Move(
        piece.info, display, Floor.SPECIAL,
        setOf(piece.pos, rook.pos), setOf(pieceTarget, rookTarget),
        ((between(piece.pos.file, pieceTarget.file) + pieceTarget.file + between(rook.pos.file, rookTarget.file) + rookTarget.file).distinct() - rook.pos.file - piece.pos.file).map { Pos(it, piece.pos.rank) }.toSet(),
        (between(piece.pos.file, pieceTarget.file) + pieceTarget.file + piece.pos.file).map { Pos(it, piece.pos.rank) }.toSet(),
        emptySet(), emptySet(),
        listOf(CastlesTrait(rook.info, side, pieceTarget, rookTarget), DefaultHalfmoveClockTrait(), CheckTrait()),
        castlesOrder
    )

    override fun generate(piece: BoardPiece): List<Move> = buildList {
        addAll(jumps(piece, rotationsOf(1, 0) + rotationsOf(1, 1)))
        if (!piece.hasMoved) {
            for (rook in piece.square.board.piecesOf(piece.side, PieceType.ROOK)) {
                if (rook.pos.rank == piece.pos.rank && !rook.hasMoved) {
                    val side = if (rook.pos.file < piece.pos.file) queenside else kingside
                    if (piece.square.game.settings.simpleCastling) {
                        TODO()
                    } else {
                        val target = when(side) {
                            BoardSide.QUEENSIDE -> piece.pos.copy(file = 2)
                            BoardSide.KINGSIDE -> piece.pos.copy(file = 6)
                        }
                        val rookTarget = when(side) {
                            BoardSide.QUEENSIDE -> piece.pos.copy(file = 3)
                            BoardSide.KINGSIDE -> piece.pos.copy(file = 5)
                        }
                        add(castles(piece, rook, side, if (piece.square.board.chess960) rook.pos else target, target, rookTarget))
                    }
                }
            }
        }
    }

}


class PawnMovement(
    private val canDouble: (PieceInfo) -> Boolean = { !it.hasMoved },
    private val promotions: (PieceInfo) -> List<Piece> = { p -> defaultPromotions.map { it.of(p.side) } }
) : MoveScheme {
    companion object {
        private val defaultPromotions = listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)
        @JvmField
        val EN_PASSANT = GregChessModule.register("en_passant", ChessFlagType { it == 1u})
        private fun ifProm(promotions: Any?, floor: Floor) = if (promotions == null) floor else Floor.SPECIAL
        private val pawnOrder = with (MoveNameTokenType) { NameOrder(listOf(UNIQUENESS_COORDINATE, CAPTURE, TARGET, PROMOTION, CHECK, CHECKMATE, EN_PASSANT)) }
    }

    override fun generate(piece: BoardPiece): List<Move> = buildList {
        fun promotions(pos: Pos) = if (pos.rank in listOf(0, 7)) promotions(piece.info) else null

        val dir = piece.side.dir
        val pos = piece.pos
        val forward = pos + dir
        if (forward.isValid()) {
            add(Move(piece.info, forward, ifProm(promotions(forward), Floor.MOVE),
                setOf(pos), setOf(forward), setOf(forward), setOf(forward),
                emptySet(), emptySet(),
                listOf(PawnOriginTrait(), PromotionTrait(promotions(forward)), TargetTrait(forward), DefaultHalfmoveClockTrait(), CheckTrait()),
                pawnOrder
            ))
        }
        val forward2 = pos + dir * 2
        if (forward2.isValid() && canDouble(piece.info)) {
            add(Move(piece.info, forward2, ifProm(promotions(forward), Floor.MOVE),
                setOf(pos), setOf(forward2), setOf(forward, forward2), setOf(forward, forward2),
                emptySet(), setOf(PosFlag(forward, ChessFlag(EN_PASSANT))),
                listOf(PawnOriginTrait(), PromotionTrait(promotions(forward2)), TargetTrait(forward2), DefaultHalfmoveClockTrait(), CheckTrait()),
                pawnOrder
            ))
        }
        for (s in BoardSide.values()) {
            val capture = pos + dir + s.dir
            if (capture.isValid()) {
                add(Move(piece.info, capture, ifProm(promotions(forward), Floor.CAPTURE),
                    setOf(pos), setOf(capture), emptySet(), setOf(capture),
                    emptySet(), emptySet(),
                    listOf(PawnOriginTrait(), PromotionTrait(promotions(capture)),
                        CaptureTrait(capture, true), TargetTrait(capture), DefaultHalfmoveClockTrait(), CheckTrait()),
                    pawnOrder
                ))
                val enPassant = pos + s.dir
                add(Move(piece.info, capture, ifProm(promotions(forward), Floor.CAPTURE),
                    setOf(pos, enPassant), setOf(capture), emptySet(), setOf(capture),
                    setOf(Pair(capture, EN_PASSANT)), emptySet(),
                    listOf(PawnOriginTrait(), PromotionTrait(promotions(capture)),
                        CaptureTrait(enPassant, true), TargetTrait(capture),
                        NameTrait(MoveName(listOf(MoveNameTokenType.EN_PASSANT.mk))), DefaultHalfmoveClockTrait(), CheckTrait()),
                    pawnOrder
                ))
            }
        }
    }
}