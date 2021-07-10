package gregc.gregchess.chess

import gregc.gregchess.LocalizedString
import java.util.*

abstract class HumanPlayer(val name: String) {
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
    abstract fun setItem(i: Int, piece: Piece?)
    abstract fun openPawnPromotionMenu(moves: List<MoveCandidate>)
    abstract fun showEndReason(side: Side, reason: EndReason)
    abstract fun showEndReason(reason: EndReason)
    abstract fun local(msg: LocalizedString): String
}

fun HumanPlayer.sendMessage(msg: LocalizedString) = sendMessage(local(msg))

fun HumanPlayer.sendTitle(title: String, subtitle: LocalizedString) = sendTitle(title, local(subtitle))
fun HumanPlayer.sendTitle(title: LocalizedString, subtitle: String = "") = sendTitle(local(title), subtitle)
fun HumanPlayer.sendTitle(title: LocalizedString, subtitle: LocalizedString) = sendTitle(local(title), local(subtitle))

abstract class MinecraftPlayer(val uniqueId: UUID, name: String) : HumanPlayer(name)
