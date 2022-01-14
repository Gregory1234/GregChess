package gregc.gregchess.chess.variant

import gregc.gregchess.chess.*
import gregc.gregchess.registry.Register

object KingOfTheHill : ChessVariant() {

    @JvmField
    @Register
    val KING_OF_THE_HILL = DetEndReason(EndReason.Type.NORMAL)

    override fun checkForGameEnd(game: ChessGame) {
        val king = game.tryOrStopNull(game.board.kingOf(game.currentTurn))
        if (king.pos.file in (3..4) && king.pos.rank in (3..4))
            game.stop(game.currentTurn.wonBy(KING_OF_THE_HILL))

        Normal.checkForGameEnd(game)
    }
}