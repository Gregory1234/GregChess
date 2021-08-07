package gregc.gregchess.chess.variant

import gregc.gregchess.*
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Chessboard

object KingOfTheHill : ChessVariant() {

    @JvmField
    val KING_OF_THE_HILL = GregChessModule.register("king_of_the_hill", DetEndReason(EndReason.Type.NORMAL))

    override fun chessboardSetup(board: Chessboard) {
        for ((x, y) in Pair(3, 3)..Pair(4, 4)) {
            board[Pos(x, y)]?.variantMarker = Floor.OTHER
        }
    }

    override fun checkForGameEnd(game: ChessGame) {
        for (p in game.board.pieces) {
            if (p.type == PieceType.KING && p.pos.file in (3..4) && p.pos.rank in (3..4))
                game.stop(p.side.wonBy(KING_OF_THE_HILL))
        }
        Normal.checkForGameEnd(game)
    }
}