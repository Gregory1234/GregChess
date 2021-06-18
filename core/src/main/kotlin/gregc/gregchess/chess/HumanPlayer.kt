package gregc.gregchess.chess

import gregc.gregchess.*
import java.util.*

abstract class HumanPlayer(val name: String) {
    abstract var isAdmin: Boolean
    var currentGame: ChessGame? = null
    var spectatedGame: ChessGame? = null
        set(v) {
            field?.spectatorLeave(this)
            field = v
            field?.spectate(this)
        }
    val games = mutableListOf<ChessGame>()

    abstract fun sendMessage(msg: String)
    abstract fun sendTitle(title: String, subtitle: String = "")
    fun isInGame(): Boolean = currentGame != null
    fun isSpectating(): Boolean = spectatedGame != null
    abstract fun sendPGN(pgn: PGN)
    abstract fun sendFEN(fen: FEN)
    abstract fun sendCommandMessage(msg: String, action: String, command: String)
    abstract fun setItem(i: Int, piece: Piece?)
    abstract fun openPawnPromotionMenu(moves: List<MoveCandidate>)
}

abstract class MinecraftPlayer(val uniqueId: UUID, name: String) : HumanPlayer(name)
