package gregc.gregchess.variant

import gregc.gregchess.Color
import gregc.gregchess.board.ChessboardView
import gregc.gregchess.game.ChessGame
import gregc.gregchess.move.Move
import gregc.gregchess.piece.BoardPiece
import gregc.gregchess.results.*

object CaptureAll : ChessVariant() {
    override fun getLegality(move: Move, board: ChessboardView): MoveLegality =
        if (Normal.isValid(move, board)) MoveLegality.LEGAL else MoveLegality.INVALID

    override fun isInCheck(king: BoardPiece, board: ChessboardView): Boolean = false

    override fun timeout(game: ChessGame, color: Color) = game.stop(color.lostBy(EndReason.TIMEOUT))

    override fun checkForGameEnd(game: ChessGame) = with(game.board) {
        if (piecesOf(!game.currentTurn).isEmpty())
            game.stop(game.currentTurn.wonBy(EndReason.ALL_PIECES_LOST))
        if (piecesOf(!game.currentTurn).all { it.getMoves(this).none { m -> game.variant.isLegal(m, this) } })
            game.stop(drawBy(EndReason.STALEMATE))

        checkForRepetition()
        checkForFiftyMoveRule()
    }
}