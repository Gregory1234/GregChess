package gregc.gregchess.chess.variant

import gregc.gregchess.*
import gregc.gregchess.chess.*
import gregc.gregchess.chess.piece.PieceType

object KingOfTheHill : ChessVariant() {

    @JvmField
    val KING_OF_THE_HILL = GregChessModule.register("king_of_the_hill", DetEndReason(EndReason.Type.NORMAL))

    override val specialSquares get() = (Pair(3, 3)..Pair(4, 4)).map { (x,y) -> Pos(x,y) }.toSet()

    override fun checkForGameEnd(game: ChessGame) {
        for (p in game.board.pieces) {
            if (p.type == PieceType.KING && p.pos.file in (3..4) && p.pos.rank in (3..4))
                game.stop(p.color.wonBy(KING_OF_THE_HILL))
        }
        Normal.checkForGameEnd(game)
    }
}