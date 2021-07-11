package gregc.gregchess.chess

import java.util.*

enum class GamePlayerStatus {
    START, IN_CHECK, TURN
}

abstract class HumanPlayer(val name: String) {
    var currentGame: ChessGame? = null
    var spectatedGame: ChessGame? = null
        set(v) {
            field?.spectatorLeave(this)
            field = v
            field?.spectate(this)
        }
    val games = mutableListOf<ChessGame>()

    fun isInGame(): Boolean = currentGame != null
    fun isSpectating(): Boolean = spectatedGame != null
    abstract fun sendPGN(pgn: PGN)
    abstract fun sendFEN(fen: FEN)
    abstract fun setItem(i: Int, piece: Piece?)
    abstract fun openPawnPromotionMenu(moves: List<MoveCandidate>)
    abstract fun showEndReason(side: Side, reason: EndReason)
    abstract fun showEndReason(reason: EndReason)
    abstract fun sendGameUpdate(side: Side, status: List<GamePlayerStatus>)
    abstract fun sendLastMoves(num:UInt, wLast: MoveData?, bLast: MoveData?)
}

abstract class MinecraftPlayer(val uniqueId: UUID, name: String) : HumanPlayer(name)
