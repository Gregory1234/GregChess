package gregc.gregchess.chess.variant

import gregc.gregchess.GregChess
import gregc.gregchess.chess.*
import gregc.gregchess.registerEndReason

object KingOfTheHill : ChessVariant() {

    @JvmField
    val KING_OF_THE_HILL = GregChess.registerEndReason("king_of_the_hill", DetEndReason(EndReason.Type.NORMAL))

    override fun checkForGameEnd(game: ChessGame) {
        val king = game.tryOrStopNull(game.board.kingOf(game.currentTurn))
        if (king.pos.file in (3..4) && king.pos.rank in (3..4))
            game.stop(game.currentTurn.wonBy(KING_OF_THE_HILL))

        Normal.checkForGameEnd(game)
    }
}