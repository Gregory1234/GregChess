package gregc.gregchess.fabric

import gregc.gregchess.fabric.chess.ChessInitializer

object GregChessModChess : ChessInitializer {
    override fun onInitializeChess() = GregChess.fullLoad()
}