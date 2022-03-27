package gregc.gregchess.chess.variant

import gregc.gregchess.chess.*
import gregc.gregchess.registry.Register
import gregc.gregchess.registry.Registering

object KingOfTheHill : ChessVariant(), Registering {

    @JvmField
    val SPECIAL_SQUARES = listOf(Pos(3, 3), Pos(3, 4), Pos(4, 3), Pos(4, 4))

    @JvmField
    @Register
    val KING_OF_THE_HILL = DetEndReason(EndReason.Type.NORMAL)

    override fun checkForGameEnd(game: ChessGame) {
        val king = game.tryOrStopNull(game.board.kingOf(game.currentTurn))
        if (king.pos in SPECIAL_SQUARES)
            game.stop(game.currentTurn.wonBy(KING_OF_THE_HILL))

        Normal.checkForGameEnd(game)
    }
}