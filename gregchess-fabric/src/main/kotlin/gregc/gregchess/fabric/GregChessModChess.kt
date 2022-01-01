package gregc.gregchess.fabric

import gregc.gregchess.GregChess
import gregc.gregchess.fabric.chess.ChessInitializer

object GregChessModChess : ChessInitializer {
    override fun onInitializeChess() = GregChess.fullLoad(listOf(GregChessFabric))
}