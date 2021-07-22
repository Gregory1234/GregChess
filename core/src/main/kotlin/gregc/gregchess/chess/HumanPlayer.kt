package gregc.gregchess.chess

import java.util.*

enum class GamePlayerStatus {
    START, IN_CHECK, TURN
}

abstract class HumanPlayer(val name: String) {
    var currentGame: ChessGame? = null
    val games = mutableListOf<ChessGame>()

    val isInGame get() = currentGame != null
    abstract fun sendPGN(pgn: PGN)
    abstract fun sendFEN(fen: FEN)
    abstract fun setItem(i: Int, piece: Piece?)
    abstract fun openPawnPromotionMenu(moves: List<MoveCandidate>)
    abstract fun showGameResults(side: Side, results: GameResults<*>)
    abstract fun showGameResults(results: GameResults<*>)
    abstract fun sendGameUpdate(side: Side, status: List<GamePlayerStatus>)
    abstract fun sendLastMoves(num:UInt, wLast: MoveData?, bLast: MoveData?)
}

abstract class MinecraftPlayer(val uuid: UUID, name: String) : HumanPlayer(name)
