package gregc.gregchess.chess.variant

import gregc.gregchess.chess.*

object CaptureAll : ChessVariant("CAPTURE_ALL") {
    override fun getLegality(move: MoveCandidate): MoveLegality =
        if (Normal.isValid(move)) MoveLegality.LEGAL else MoveLegality.INVALID

    override fun isInCheck(king: BoardPiece): Boolean = false

    override fun timeout(game: ChessGame, side: Side) = game.stop(side.lostBy(EndReason.TIMEOUT))

    override fun checkForGameEnd(game: ChessGame) = with(game.board) {
        if (piecesOf(!game.currentTurn).all { getMoves(it.pos).none(game.variant::isLegal) }) {
            game.stop(drawBy(EndReason.STALEMATE))
        }
        checkForRepetition()
        checkForFiftyMoveRule()
        if (piecesOf(!game.currentTurn).isEmpty())
            game.stop(game.currentTurn.wonBy(EndReason.ALL_PIECES_LOST))
    }
}