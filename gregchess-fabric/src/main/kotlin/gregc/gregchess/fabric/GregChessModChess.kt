package gregc.gregchess.fabric

import gregc.gregchess.GregChess
import gregc.gregchess.fabric.chess.ChessInitializer

object GregChessModChess : ChessInitializer {
    override fun onInitializeChess() {
        GregChess.logger = Log4jGregLogger(GregChessMod.logger)
        GregChess.fullLoad(listOf(GregChessFabric))
    }
}