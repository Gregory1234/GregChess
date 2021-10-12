package gregc.gregchess.fabric

import gregc.gregchess.GregChessModule
import gregc.gregchess.fabric.chess.ChessInitializer
import gregc.gregchess.fabric.coroutines.FabricDispatcher
import gregc.gregchess.gregChessCoroutineDispatcherFactory

object GregChessChess : ChessInitializer {
    override fun onInitializeChess() {
        GregChessModule.logger = Log4jGregLogger(GregChess.logger)
        gregChessCoroutineDispatcherFactory = { FabricDispatcher() }
        GregChessModule.extensions += FabricGregChessModule
        GregChessModule.fullLoad()
    }
}