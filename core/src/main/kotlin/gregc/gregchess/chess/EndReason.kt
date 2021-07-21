package gregc.gregchess.chess

import gregc.gregchess.Identifier
import gregc.gregchess.asIdent

open class EndReason(val id: Identifier, val type: Type, val winner: Side? = null, val quick: Boolean = false, val args: List<Any?> = emptyList()) {

    enum class Type(val pgn: String) {
        NORMAL("normal"), ABANDONED("abandoned"), TIME_FORFEIT("time forfeit"), EMERGENCY("emergency")
    }

    override fun toString() = id.toString()

    class Checkmate(winner: Side) : EndReason("checkmate".asIdent(), Type.NORMAL, winner)
    class Resignation(winner: Side) : EndReason("resignation".asIdent(), Type.ABANDONED, winner)
    class Walkover(winner: Side) : EndReason("walkover".asIdent(), Type.ABANDONED, winner)
    class Stalemate : EndReason("stalemate".asIdent(), Type.NORMAL)
    class InsufficientMaterial : EndReason("insufficient_material".asIdent(), Type.NORMAL)
    class FiftyMoves : EndReason("fifty_moves".asIdent(), Type.NORMAL)
    class Repetition : EndReason("repetition".asIdent(), Type.NORMAL)
    class DrawAgreement : EndReason("draw_agreement".asIdent(), Type.NORMAL)
    class Timeout(winner: Side) : EndReason("timeout".asIdent(), Type.TIME_FORFEIT, winner)
    class DrawTimeout : EndReason("draw_timeout".asIdent(), Type.TIME_FORFEIT)
    class AllPiecesLost(winner: Side) : EndReason("pieces_lost".asIdent(), Type.NORMAL, winner)
    class Error(val e: Exception) : EndReason("error".asIdent(), Type.EMERGENCY, args = listOf(e))

    val reasonPGN = type.pgn

    val winnerPGN
        get() = when (winner) {
            Side.WHITE -> "1-0"
            Side.BLACK -> "0-1"
            null -> "1/2-1/2"
        }
}