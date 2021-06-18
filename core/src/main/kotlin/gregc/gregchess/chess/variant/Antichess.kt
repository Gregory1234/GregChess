package gregc.gregchess.chess.variant

import gregc.gregchess.chess.*

object Antichess : ChessVariant("Antichess") {
    class Stalemate(winner: Side) : EndReason(EndReasonConfig::stalemate, "normal", winner)

    override fun getLegality(move: MoveCandidate): MoveLegality {
        if (!Normal.isValid(move))
            return MoveLegality.INVALID
        if (move.piece.type == PieceType.KING && move.help.isNotEmpty())
            return MoveLegality.INVALID
        if (move.captured != null)
            return MoveLegality.LEGAL
        return if (move.board.piecesOf(move.piece.side).none { m ->
                m.square.bakedMoves.orEmpty().filter { Normal.isValid(it) }.any { it.captured != null }
            }) MoveLegality.LEGAL else MoveLegality.SPECIAL
    }

    override fun isInCheck(king: BoardPiece) = false

    override fun isInCheck(game: ChessGame, side: Side) = false

    override fun checkForGameEnd(game: ChessGame) = with(game.board) {
        if (piecesOf(!game.currentTurn).isEmpty())
            game.stop(EndReason.AllPiecesLost(!game.currentTurn))
        if (piecesOf(!game.currentTurn).all { getMoves(it.pos).none(game.variant::isLegal) })
            game.stop(Stalemate(!game.currentTurn))
        checkForRepetition()
        checkForFiftyMoveRule()
    }

    override fun timeout(game: ChessGame, side: Side) = game.stop(EndReason.Timeout(side))

    override fun promotions(piece: Piece): Collection<Piece>? =
        Normal.promotions(piece)?.plus(Piece(PieceType.KING, piece.side))
}