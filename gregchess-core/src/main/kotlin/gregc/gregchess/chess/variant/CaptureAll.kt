package gregc.gregchess.chess.variant

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Chessboard
import gregc.gregchess.chess.move.Move
import gregc.gregchess.chess.piece.BoardPiece

object CaptureAll : ChessVariant() {
    override fun getLegality(move: Move, game: ChessGame): MoveLegality =
        if (Normal.isValid(move, game)) MoveLegality.LEGAL else MoveLegality.INVALID

    override fun isInCheck(king: BoardPiece, board: Chessboard): Boolean = false

    override fun timeout(game: ChessGame, color: Color) = game.stop(color.lostBy(EndReason.TIMEOUT))

    override fun checkForGameEnd(game: ChessGame) = with(game.board) {
        if (piecesOf(!game.currentTurn).isEmpty())
            game.stop(game.currentTurn.wonBy(EndReason.ALL_PIECES_LOST))
        if (piecesOf(!game.currentTurn).all { it.getMoves(this).none { m -> game.variant.isLegal(m, game) } })
            game.stop(drawBy(EndReason.STALEMATE))

        checkForRepetition()
        checkForFiftyMoveRule()
    }
}