package gregc.gregchess.chess.variant

import gregc.gregchess.*
import gregc.gregchess.chess.*

object KingOfTheHill : ChessVariant() {

    @JvmField
    val KING_OF_THE_HILL = GregChessModule.register("king_of_the_hill", DetEndReason(EndReason.Type.NORMAL))

    override val specialSquares get() = (Pair(3, 3)..Pair(4, 4)).map { (x,y) -> Pos(x,y) }.toSet()

    override fun checkForGameEnd(game: ChessGame) {
        val king = game.tryOrStopNull(game.board.kingOf(game.currentTurn))
        if (king.pos.file in (3..4) && king.pos.rank in (3..4))
            game.stop(game.currentTurn.wonBy(KING_OF_THE_HILL))

        Normal.checkForGameEnd(game)
    }
}