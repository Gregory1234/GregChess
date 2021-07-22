package gregc.gregchess.chess.variant

import gregc.gregchess.asIdent
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Chessboard
import gregc.gregchess.rangeTo

object KingOfTheHill : ChessVariant("king_of_the_hill".asIdent()) {

    private val KING_OF_THE_HILL = DetEndReason("king_of_the_hill".asIdent(), EndReason.Type.NORMAL)

    override fun chessboardSetup(board: Chessboard) {
        (Pair(3, 3)..Pair(4, 4)).forEach { (x, y) ->
            board[Pos(x, y)]?.variantMarker = Floor.OTHER
        }
    }

    override fun checkForGameEnd(game: ChessGame) {
        game.board.pieces.filter { it.type == PieceType.KING }.forEach {
            if (it.pos.file in (3..4) && it.pos.rank in (3..4))
                game.stop(it.side.wonBy(KING_OF_THE_HILL))
        }
        Normal.checkForGameEnd(game)
    }
}