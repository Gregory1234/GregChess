package gregc.gregchess.chess.variant

import gregc.gregchess.board.ChessboardView
import gregc.gregchess.chess.*
import gregc.gregchess.chess.piece.BoardPiece
import gregc.gregchess.chess.piece.PieceType
import gregc.gregchess.game.ChessGame
import gregc.gregchess.move.Move
import gregc.gregchess.move.scheme.pawnMovement
import gregc.gregchess.move.scheme.promotions
import gregc.gregchess.move.trait.captureTrait
import gregc.gregchess.move.trait.castlesTrait
import gregc.gregchess.util.Register
import gregc.gregchess.util.Registering

object Antichess : ChessVariant(), Registering {
    @JvmField
    @Register
    val STALEMATE_VICTORY = DetEndReason(EndReason.Type.NORMAL)

    @JvmField
    val PROMOTIONS = Normal.PROMOTIONS + PieceType.KING

    override fun getPieceMoves(piece: BoardPiece, board: ChessboardView): List<Move> = when (piece.type) {
        PieceType.PAWN -> pawnMovement(piece).promotions(PROMOTIONS)
        PieceType.KING -> Normal.getPieceMoves(piece, board).filter { it.castlesTrait == null }
        else -> Normal.getPieceMoves(piece, board)
    }

    override fun getLegality(move: Move, board: ChessboardView): MoveLegality {
        if (!Normal.isValid(move, board))
            return MoveLegality.INVALID

        if (move.captureTrait?.capture?.let { board[it] } != null)
            return MoveLegality.LEGAL

        return if (board.piecesOf(move.main.color).flatMap { it.getMoves(board) }
                .filter { Normal.isValid(it, board) }
                .all { m -> m.captureTrait?.capture?.let { board[it] } == null })
            MoveLegality.LEGAL
        else
            MoveLegality.SPECIAL
    }

    override fun isInCheck(king: BoardPiece, board: ChessboardView) = false

    override fun isInCheck(board: ChessboardView, color: Color) = false

    override fun checkForGameEnd(game: ChessGame) = with(game.board) {
        if (piecesOf(!game.currentTurn).isEmpty())
            game.stop(game.currentTurn.lostBy(EndReason.ALL_PIECES_LOST))

        if (piecesOf(!game.currentTurn).all { it.getMoves(this).none { m -> game.variant.isLegal(m, this) } })
            game.stop(game.currentTurn.lostBy(STALEMATE_VICTORY))

        checkForRepetition()
        checkForFiftyMoveRule()
    }

    override fun timeout(game: ChessGame, color: Color) = game.stop(color.wonBy(EndReason.TIMEOUT))
}