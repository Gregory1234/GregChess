package gregc.gregchess.fabric

import gregc.gregchess.GregChessModule
import gregc.gregchess.fabric.chess.ChessInitializer

object GregChessChess : ChessInitializer {
    override fun onInitializeChess() {
        GregChessModule.extensions += FabricGregChessModule
        GregChessModule.load()
    }
}