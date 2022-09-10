package gregc.gregchess.variant

import gregc.gregchess.*
import gregc.gregchess.match.ChessMatch
import gregc.gregchess.results.*

object KingOfTheHill : ChessVariant(), Registering {

    @JvmField
    val SPECIAL_SQUARES = listOf(Pos(3, 3), Pos(3, 4), Pos(4, 3), Pos(4, 4))

    @JvmField
    @Register
    val KING_OF_THE_HILL = DetEndReason(EndReason.Type.NORMAL)

    override fun checkForMatchEnd(match: ChessMatch) {
        val king = match.tryOrStopNull(match.board.kingOf(match.currentColor))
        if (king.pos in SPECIAL_SQUARES)
            match.stop(match.currentColor.wonBy(KING_OF_THE_HILL))

        Normal.checkForMatchEnd(match)
    }
}