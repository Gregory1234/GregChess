package gregc.gregchess.chess.variant

import gregc.gregchess.chess.ChessGame
import gregc.gregchess.chess.ChessPosition
import gregc.gregchess.chess.ChessSide
import gregc.gregchess.chess.ChessType
import gregc.gregchess.chess.component.Chessboard
import gregc.gregchess.rangeTo
import org.bukkit.Material

object KingOfTheHill : ChessVariant("KingOfTheHill") {

    class KingOfTheHillEndReason(winner: ChessSide) :
        ChessGame.EndReason("Chess.EndReason.KingOfTheHill", "normal", winner)

    override fun chessboardSetup(board: Chessboard) {
        (Pair(3, 3)..Pair(4, 4)).forEach { (x, y) ->
            board[ChessPosition(x, y)]?.variantMarker = Material.PURPLE_CONCRETE
            board[ChessPosition(x, y)]?.render()
        }
    }

    override fun checkForGameEnd(game: ChessGame) {
        game.board.pieces.filter { it.type == ChessType.KING }.forEach {
            if (it.pos.file in (3..4) && it.pos.rank in (3..4))
                game.stop(KingOfTheHillEndReason(it.side))
        }
        Normal.checkForGameEnd(game)
    }
}