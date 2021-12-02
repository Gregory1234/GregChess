package gregc.gregchess.fabric

import gregc.gregchess.GregChessModule
import gregc.gregchess.fabric.chess.ChessInitializer

object GregChessChess : ChessInitializer {
    override fun onInitializeChess() {
        GregChessModule.logger = Log4jGregLogger(GregChess.logger)
        GregChessModule.extensions += FabricGregChessModule
        GregChessModule.fullLoad()
    }
}