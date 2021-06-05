package gregc.gregchess.chess.variant

import gregc.gregchess.rangeTo
import gregc.gregchess.Config
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Chessboard

object KingOfTheHill : ChessVariant("KingOfTheHill") {

    class KingOfTheHillEndReason(winner: Side) : ChessGame.EndReason(Config.Chess.EndReason.kingOfTheHill, "normal", winner)

    override fun chessboardSetup(board: Chessboard) {
        (Pair(3, 3)..Pair(4, 4)).forEach { (x, y) ->
            board[Pos(x, y)]?.variantMarker = Floor.OTHER
            board[Pos(x, y)]?.render()
        }
    }

    override fun checkForGameEnd(game: ChessGame) {
        game.board.pieces.filter { it.type == PieceType.KING }.forEach {
            if (it.pos.file in (3..4) && it.pos.rank in (3..4))
                game.stop(KingOfTheHillEndReason(it.side))
        }
        Normal.checkForGameEnd(game)
    }
}