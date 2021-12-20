package gregc.gregchess.chess.variant

import gregc.gregchess.GregChess
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Chessboard
import gregc.gregchess.chess.move.*
import gregc.gregchess.chess.piece.BoardPiece
import gregc.gregchess.chess.piece.PieceType
import gregc.gregchess.register

object Antichess : ChessVariant() {
    @JvmField
    val STALEMATE_VICTORY = GregChess.register("stalemate_victory", DetEndReason(EndReason.Type.NORMAL))

    @JvmField
    val PROMOTIONS = Normal.PROMOTIONS + PieceType.KING

    override fun getPieceMoves(piece: BoardPiece, board: Chessboard): List<Move> = when (piece.type) {
        PieceType.PAWN -> pawnMovement(piece).promotions(PROMOTIONS)
        PieceType.KING -> Normal.getPieceMoves(piece, board).filter { it.getTrait<CastlesTrait>() == null }
        else -> Normal.getPieceMoves(piece, board)
    }

    override fun getLegality(move: Move, game: ChessGame): MoveLegality {
        if (!Normal.isValid(move, game))
            return MoveLegality.INVALID

        if (move.getTrait<CaptureTrait>()?.capture?.let { game.board[it] } != null)
            return MoveLegality.LEGAL

        return if (game.board.piecesOf(move.piece.color).flatMap { it.getMoves(game.board) }
                .filter { Normal.isValid(it, game) }
                .all { m -> m.getTrait<CaptureTrait>()?.capture?.let { game.board[it] } == null })
            MoveLegality.LEGAL
        else
            MoveLegality.SPECIAL
    }

    override fun isInCheck(king: BoardPiece, board: Chessboard) = false

    override fun isInCheck(game: ChessGame, color: Color) = false

    override fun checkForGameEnd(game: ChessGame) = with(game.board) {
        if (piecesOf(!game.currentTurn).isEmpty())
            game.stop(game.currentTurn.lostBy(EndReason.ALL_PIECES_LOST))

        if (piecesOf(!game.currentTurn).all { it.getMoves(this).none { m -> game.variant.isLegal(m, game) } })
            game.stop(game.currentTurn.lostBy(STALEMATE_VICTORY))

        checkForRepetition()
        checkForFiftyMoveRule()
    }

    override fun timeout(game: ChessGame, color: Color) = game.stop(color.wonBy(EndReason.TIMEOUT))
}