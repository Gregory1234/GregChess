package gregc.gregchess.variant

import gregc.gregchess.*
import gregc.gregchess.board.ChessboardView
import gregc.gregchess.match.ChessMatch
import gregc.gregchess.move.*
import gregc.gregchess.move.trait.captureTrait
import gregc.gregchess.move.trait.castlesTrait
import gregc.gregchess.piece.BoardPiece
import gregc.gregchess.piece.PieceType
import gregc.gregchess.results.*

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

    override fun checkForMatchEnd(match: ChessMatch) = with(match.board) {
        if (piecesOf(!match.currentTurn).isEmpty())
            match.stop(match.currentTurn.lostBy(EndReason.ALL_PIECES_LOST))

        if (piecesOf(!match.currentTurn).all { it.getMoves(this).none { m -> match.variant.isLegal(m, this) } })
            match.stop(match.currentTurn.lostBy(STALEMATE_VICTORY))

        checkForRepetition()
        checkForFiftyMoveRule()
    }

    override fun timeout(match: ChessMatch, color: Color) = match.stop(color.wonBy(EndReason.TIMEOUT))
}