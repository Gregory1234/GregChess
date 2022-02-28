package gregc.gregchess.fabric

object GregChessModChess : ChessInitializer {
    override fun onInitializeChess() = GregChess.fullLoad()
}