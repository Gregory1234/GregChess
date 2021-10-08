package gregc.gregchess.chess

import gregc.gregchess.*
import gregc.gregchess.chess.component.Chessboard
import kotlin.math.abs

fun defaultColor(pos: Pos, board: Chessboard) = if (board[pos]?.piece == null) Floor.MOVE else Floor.CAPTURE

val defaultOrder =
    with(MoveNameTokenType) { nameOrder(PIECE_TYPE, UNIQUENESS_COORDINATE, CAPTURE, TARGET, CHECK, CHECKMATE) }

fun jumps(piece: BoardPiece, board: Chessboard, dirs: Collection<Dir>) =
    dirs.map { piece.pos + it }.filter { it.isValid() }.map {
        Move(
            piece, it, defaultColor(it, board),
            setOf(piece.pos), setOf(it), emptySet(), setOf(it), emptySet(),
            listOf(PieceOriginTrait(), CaptureTrait(it), TargetTrait(it), DefaultHalfmoveClockTrait(), CheckTrait()),
            defaultOrder,
        )
    }

fun rays(piece: BoardPiece, board: Chessboard, dirs: Collection<Dir>) =
    dirs.flatMap { dir ->
        PosSteps(piece.pos + dir, dir).mapIndexedNotNull { index, it ->
            Move(
                piece, it, defaultColor(it, board),
                setOf(piece.pos), setOf(it),
                PosSteps(piece.pos + dir, dir, index), PosSteps(piece.pos + dir, dir, index + 1),
                emptySet(),
                listOf(
                    PieceOriginTrait(), CaptureTrait(it), TargetTrait(it), DefaultHalfmoveClockTrait(), CheckTrait()
                ),
                defaultOrder
            )
        }
    }


fun interface MoveScheme {
    fun generate(piece: BoardPiece, board: Chessboard): List<Move>
}

class JumpMovement(private val dirs: Collection<Dir>) : MoveScheme {
    override fun generate(piece: BoardPiece, board: Chessboard): List<Move> = jumps(piece, board, dirs)
}

class RayMovement(private val dirs: Collection<Dir>) : MoveScheme {
    override fun generate(piece: BoardPiece, board: Chessboard): List<Move> = rays(piece, board, dirs)
}

object KingMovement : MoveScheme {

    private val castlesOrder =
        nameOrder(MoveNameTokenType.CASTLE, MoveNameTokenType.CHECK, MoveNameTokenType.CHECKMATE)

    private fun castles(
        piece: BoardPiece, rook: BoardPiece, side: BoardSide, display: Pos, pieceTarget: Pos, rookTarget: Pos
    ) = Move(
        piece, display, Floor.SPECIAL,
        setOf(piece.pos, rook.pos), setOf(pieceTarget, rookTarget),
        neededEmptyFiles(piece.pos.file, pieceTarget.file, rook.pos.file, rookTarget.file).toPosSet(piece.pos.rank),
        betweenInc(piece.pos.file, pieceTarget.file).toList().toPosSet(piece.pos.rank), emptySet(),
        listOf(CastlesTrait(rook, side, pieceTarget, rookTarget), DefaultHalfmoveClockTrait(), CheckTrait()),
        castlesOrder
    )

    private fun Collection<Int>.toPosSet(rank: Int) = map { Pos(it, rank) }.toSet()

    private fun neededEmptyFiles(piece: Int, pieceTarget: Int, rook: Int, rookTarget: Int) =
        (between(piece, pieceTarget) + pieceTarget + between(rook, rookTarget) + rookTarget).distinct() - rook - piece

    private fun normalCastles(piece: BoardPiece, rook: BoardPiece, side: BoardSide, chess960: Boolean): Move {
        val target = when (side) {
            BoardSide.QUEENSIDE -> piece.pos.copy(file = 2)
            BoardSide.KINGSIDE -> piece.pos.copy(file = 6)
        }
        val rookTarget = when (side) {
            BoardSide.QUEENSIDE -> piece.pos.copy(file = 3)
            BoardSide.KINGSIDE -> piece.pos.copy(file = 5)
        }
        return castles(piece, rook, side, if (chess960) rook.pos else target, target, rookTarget)
    }

    private fun simplifiedCastles(piece: BoardPiece, rook: BoardPiece, side: BoardSide): Move =
        if (abs(piece.pos.file-rook.pos.file) == 1) {
            castles(piece, rook, side, rook.pos, rook.pos, piece.pos)
        } else {
            val target = when (side) {
                BoardSide.QUEENSIDE -> piece.pos.copy(file = piece.pos.file - 2)
                BoardSide.KINGSIDE -> piece.pos.copy(file = piece.pos.file + 2)
            }
            val rookTarget = when (side) {
                BoardSide.QUEENSIDE -> piece.pos.copy(file = piece.pos.file - 1)
                BoardSide.KINGSIDE -> piece.pos.copy(file = piece.pos.file + 2)
            }
            castles(piece, rook, side, target, target, rookTarget)
        }

    override fun generate(piece: BoardPiece, board: Chessboard): List<Move> = buildList {
        addAll(jumps(piece, board, rotationsOf(1, 0) + rotationsOf(1, 1)))
        if (!piece.hasMoved) {
            for (rook in board.piecesOf(piece.color, PieceType.ROOK)) {
                if (rook.pos.rank == piece.pos.rank && !rook.hasMoved) {
                    val side = if (rook.pos.file < piece.pos.file) BoardSide.QUEENSIDE else BoardSide.KINGSIDE
                    add(
                        if (board.simpleCastling) simplifiedCastles(piece, rook, side)
                        else normalCastles(piece, rook, side, board.chess960)
                    )
                }
            }
        }
    }

}

class PromotionMovement(
    private val base: MoveScheme,
    private val promotions: (BoardPiece) -> List<Piece>?
) : MoveScheme {
    constructor(base: MoveScheme, promotions: List<PieceType>) : this(base, simplePromotions(promotions))

    companion object {
        fun simplePromotions(promotions: List<PieceType>): (BoardPiece) -> List<Piece>? = { p ->
            if (p.pos.rank in listOf(0,7)) promotions.map { white(it) } else null
        }
    }

    override fun generate(piece: BoardPiece, board: Chessboard): List<Move> = base.generate(piece, board).map {
        val p = promotions(piece.copy(pos = it.getTrait<TargetTrait>()?.target ?: piece.pos))
        if (p == null) it else it.copy(floor = Floor.SPECIAL, traits = it.traits + PromotionTrait(p))
    }
}


class PawnMovement(
    private val canDouble: (BoardPiece) -> Boolean = { !it.hasMoved },
) : MoveScheme {
    companion object {
        @JvmField
        val EN_PASSANT = GregChessModule.register("en_passant", ChessFlagType { it == 1u })
        private val pawnOrder = with (MoveNameTokenType) {
            nameOrder(UNIQUENESS_COORDINATE, CAPTURE, TARGET, PROMOTION, CHECK, CHECKMATE, EN_PASSANT)
        }
    }

    override fun generate(piece: BoardPiece, board: Chessboard): List<Move> = buildList {
        val dir = piece.color.forward
        val pos = piece.pos
        val forward = pos + dir
        if (forward.isValid()) {
            add(
                Move(
                    piece, forward, Floor.MOVE,
                    setOf(pos), setOf(forward), setOf(forward), setOf(forward), emptySet(),
                    listOf(PawnOriginTrait(), TargetTrait(forward), DefaultHalfmoveClockTrait(), CheckTrait()),
                    pawnOrder
                )
            )
        }
        val forward2 = pos + dir * 2
        if (forward2.isValid() && canDouble(piece)) {
            add(
                Move(
                    piece, forward2, Floor.MOVE,
                    setOf(pos), setOf(forward2), setOf(forward, forward2), setOf(forward, forward2), emptySet(),
                    listOf(
                        PawnOriginTrait(), TargetTrait(forward2), DefaultHalfmoveClockTrait(), CheckTrait(),
                        FlagTrait(listOf(PosFlag(forward, ChessFlag(EN_PASSANT))))
                    ),
                    pawnOrder
                )
            )
        }
        for (s in BoardSide.values()) {
            val capture = pos + dir + s.dir
            if (capture.isValid()) {
                add(
                    Move(
                        piece, capture, Floor.CAPTURE,
                        setOf(pos), setOf(capture), emptySet(), setOf(capture), emptySet(),
                        listOf(
                            PawnOriginTrait(), CaptureTrait(capture, true),
                            TargetTrait(capture), DefaultHalfmoveClockTrait(), CheckTrait()
                        ),
                        pawnOrder
                    )
                )
                val enPassant = pos + s.dir
                add(
                    Move(
                        piece, capture, Floor.CAPTURE,
                        setOf(pos, enPassant), setOf(capture), emptySet(), setOf(capture),
                        setOf(Pair(capture, EN_PASSANT)),
                        listOf(
                            PawnOriginTrait(), CaptureTrait(enPassant, true), TargetTrait(capture),
                            NameTrait(nameOf(MoveNameTokenType.EN_PASSANT.mk)),
                            DefaultHalfmoveClockTrait(), CheckTrait()
                        ),
                        pawnOrder
                    )
                )
            }
        }
    }
}