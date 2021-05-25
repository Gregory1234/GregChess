package gregc.gregchess.chess.variant

import gregc.gregchess.chess.ChessGame
import gregc.gregchess.chess.Pos
import gregc.gregchess.chess.Side
import gregc.gregchess.chess.PieceType
import gregc.gregchess.chess.component.Chessboard
import gregc.gregchess.rangeTo
import org.bukkit.Material

object KingOfTheHill : ChessVariant("KingOfTheHill") {

    class KingOfTheHillEndReason(winner: Side) :
        ChessGame.EndReason("Chess.EndReason.KingOfTheHill", "normal", winner)

    override fun chessboardSetup(board: Chessboard) {
        (Pair(3, 3)..Pair(4, 4)).forEach { (x, y) ->
            board[Pos(x, y)]?.variantMarker = Material.PURPLE_CONCRETE
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