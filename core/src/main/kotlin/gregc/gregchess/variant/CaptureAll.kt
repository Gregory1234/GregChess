package gregc.gregchess.variant

import gregc.gregchess.Color
import gregc.gregchess.board.ChessboardView
import gregc.gregchess.match.ChessMatch
import gregc.gregchess.move.Move
import gregc.gregchess.piece.BoardPiece
import gregc.gregchess.results.*

object CaptureAll : ChessVariant() {
    override fun getLegality(move: Move, board: ChessboardView): MoveLegality =
        if (Normal.isValid(move, board)) MoveLegality.LEGAL else MoveLegality.INVALID

    override fun isInCheck(king: BoardPiece, board: ChessboardView): Boolean = false

    override fun timeout(match: ChessMatch, color: Color) = match.stop(color.lostBy(EndReason.TIMEOUT))

    override fun checkForMatchEnd(match: ChessMatch) = with(match.board) {
        if (piecesOf(!match.currentTurn).isEmpty())
            match.stop(match.currentTurn.wonBy(EndReason.ALL_PIECES_LOST))
        if (piecesOf(!match.currentTurn).all { it.getMoves(this).none { m -> match.variant.isLegal(m, this) } })
            match.stop(drawBy(EndReason.STALEMATE))

        checkForRepetition()
        checkForFiftyMoveRule()
    }
}