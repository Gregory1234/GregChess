package gregc.gregchess.chess.variant

import gregc.gregchess.chess.*

object Antichess : ChessVariant("Antichess") {
    class Stalemate(winner: ChessSide) :
        ChessGame.EndReason("Chess.EndReason.Stalemate", "normal", winner)

    override fun getLegality(move: MoveCandidate): MoveLegality {
        if (!Normal.isValid(move))
            return MoveLegality.INVALID
        if (move.piece.type == ChessType.KING && move.help.isNotEmpty())
            return MoveLegality.INVALID
        if (move.captured != null)
            return MoveLegality.LEGAL
        return if (move.board.piecesOf(move.piece.side).none { m ->
                m.square.bakedMoves.orEmpty().filter { Normal.isValid(it) }
                    .any { it.captured != null }
            }) MoveLegality.LEGAL else MoveLegality.SPECIAL
    }

    override fun isInCheck(king: ChessPiece) = false

    override fun isInCheck(game: ChessGame, side: ChessSide) = false

    override fun checkForGameEnd(game: ChessGame) {
        if (game.board.piecesOf(!game.currentTurn).isEmpty())
            game.stop(ChessGame.EndReason.AllPiecesLost(!game.currentTurn))
        if (game.board.piecesOf(!game.currentTurn)
                .all { game.board.getMoves(it.pos).none(game.variant::isLegal) }
        ) {
            game.stop(Stalemate(!game.currentTurn))
        }
        game.board.checkForRepetition()
        game.board.checkForFiftyMoveRule()
    }

    override fun timeout(game: ChessGame, side: ChessSide) =
        game.stop(ChessGame.EndReason.Timeout(side))

    override val promotions: Collection<ChessType>
        get() = Normal.promotions + ChessType.KING
}