package gregc.gregchess.variant

import gregc.gregchess.Color
import gregc.gregchess.Registering
import gregc.gregchess.board.FEN
import gregc.gregchess.match.ChessMatch
import gregc.gregchess.move.*
import gregc.gregchess.move.connector.ChessboardView
import gregc.gregchess.move.trait.captureTrait
import gregc.gregchess.move.trait.castlesTrait
import gregc.gregchess.piece.BoardPiece
import gregc.gregchess.piece.PieceType
import gregc.gregchess.registry.Register
import gregc.gregchess.results.*

object Antichess : ChessVariant(), Registering {
    @JvmField
    @Register
    val STALEMATE_VICTORY = DetEndReason(EndReason.Type.NORMAL)

    @JvmField
    val PROMOTIONS = Normal.PROMOTIONS + PieceType.KING

    override fun getPieceMoves(piece: BoardPiece, board: ChessboardView, variantOptions: Long): List<Move> = when (piece.type) {
        PieceType.PAWN -> pawnMovement(piece).promotions(PROMOTIONS)
        PieceType.KING -> Normal.getPieceMoves(piece, board, variantOptions).filter { it.castlesTrait == null }
        else -> Normal.getPieceMoves(piece, board, variantOptions)
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
        if (piecesOf(!match.currentColor).isEmpty())
            match.stop(match.currentColor.lostBy(EndReason.ALL_PIECES_LOST))

        if (piecesOf(!match.currentColor).all { it.getMoves(this).none { m -> match.variant.isLegal(m, this) } })
            match.stop(match.currentColor.lostBy(STALEMATE_VICTORY))

        checkForRepetition()
        checkForFiftyMoveRule()
    }

    override fun timeout(match: ChessMatch, color: Color) = match.stop(color.wonBy(EndReason.TIMEOUT))

    override fun validateFEN(fen: FEN, variantOptions: Long) {
    }
}