package gregc.gregchess.chess

import gregc.gregchess.DEFAULT_LANG
import gregc.gregchess.LocalizedString
import java.util.*

abstract class HumanPlayer(val name: String) {
    abstract var isAdmin: Boolean
    var lang: String = DEFAULT_LANG
    var currentGame: ChessGame? = null
    var spectatedGame: ChessGame? = null
        set(v) {
            field?.spectatorLeave(this)
            field = v
            field?.spectate(this)
        }
    val games = mutableListOf<ChessGame>()

    abstract fun sendMessage(msg: String)
    fun local(msg: LocalizedString): String = msg.get(lang)
    fun sendMessage(msg: LocalizedString) = sendMessage(local(msg))
    abstract fun sendTitle(title: String, subtitle: String = "")
    fun isInGame(): Boolean = currentGame != null
    fun isSpectating(): Boolean = spectatedGame != null
    abstract fun sendPGN(pgn: PGN)
    abstract fun sendFEN(fen: FEN)
    abstract fun sendCommandMessage(msg: String, action: String, command: String)
    fun sendCommandMessage(msg: LocalizedString, action: LocalizedString, command: String) =
        sendCommandMessage(local(msg), local(action), command)
    abstract fun setItem(i: Int, piece: Piece?)
    abstract fun openPawnPromotionMenu(moves: List<MoveCandidate>)
}

abstract class MinecraftPlayer(val uniqueId: UUID, name: String) : HumanPlayer(name)
