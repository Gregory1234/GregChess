package gregc.gregchess.chess.variant

import gregc.gregchess.GregChessModule
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Chessboard
import gregc.gregchess.register

object Antichess : ChessVariant() {
    @JvmField
    val STALEMATE_VICTORY = GregChessModule.register("stalemate_victory", DetEndReason(EndReason.Type.NORMAL))

    private val promotions = listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT, PieceType.KING)

    override fun getPieceMoves(piece: PieceInfo, board: Chessboard): List<Move> = when (piece.type) {
        PieceType.PAWN -> PawnMovement(promotions = { p -> promotions.map { it.of(p.side) } }).generate(piece, board)
        PieceType.KING -> Normal.getPieceMoves(piece, board).filter { it.getTrait<CastlesTrait>() == null }
        else -> Normal.getPieceMoves(piece, board)
    }

    override fun getLegality(move: Move, game: ChessGame): MoveLegality {
        if (!Normal.isValid(move, game))
            return MoveLegality.INVALID
        if (move.getTrait<CaptureTrait>()?.capture?.let { game.board[it]?.piece } != null)
            return MoveLegality.LEGAL
        return if (game.board.piecesOf(move.piece.side).none { m ->
                m.square.bakedMoves.orEmpty().filter { Normal.isValid(it, game) }
                    .any { mv -> mv.getTrait<CaptureTrait>()?.capture?.let { game.board[it]?.piece } != null }
            }) MoveLegality.LEGAL else MoveLegality.SPECIAL
    }

    override fun isInCheck(king: PieceInfo, board: Chessboard) = false

    override fun isInCheck(game: ChessGame, side: Side) = false

    override fun checkForGameEnd(game: ChessGame) = with(game.board) {
        if (piecesOf(!game.currentTurn).isEmpty())
            game.stop(game.currentTurn.lostBy(EndReason.ALL_PIECES_LOST))
        if (piecesOf(!game.currentTurn).all { getMoves(it.pos).none { m -> game.variant.isLegal(m, game) } })
            game.stop(game.currentTurn.lostBy(STALEMATE_VICTORY))
        checkForRepetition()
        checkForFiftyMoveRule()
    }

    override fun timeout(game: ChessGame, side: Side) = game.stop(side.wonBy(EndReason.TIMEOUT))
}