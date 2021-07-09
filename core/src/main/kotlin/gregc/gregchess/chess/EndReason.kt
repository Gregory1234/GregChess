package gregc.gregchess.chess

open class EndReason(val standardName: String, val reasonPGN: String, val winner: Side? = null, val quick: Boolean = false) {

    override fun toString() = standardName

    class Checkmate(winner: Side) : EndReason("Checkmate", "normal", winner)
    class Resignation(winner: Side) : EndReason("Resignation", "abandoned", winner)
    class Walkover(winner: Side) : EndReason("Walkover", "abandoned", winner)
    class Stalemate : EndReason("Stalemate", "normal")
    class InsufficientMaterial : EndReason("InsufficientMaterial", "normal")
    class FiftyMoves : EndReason("FiftyMoves", "normal")
    class Repetition : EndReason("Repetition", "normal")
    class DrawAgreement : EndReason("DrawAgreement", "normal")
    class Timeout(winner: Side) : EndReason("Timeout", "time forfeit", winner)
    class DrawTimeout : EndReason("DrawTimeout", "time forfeit")
    class AllPiecesLost(winner: Side) : EndReason("PiecesLost", "normal", winner)
    class Error(val e: Exception) : EndReason("Error", "emergency") {
        override fun toString() = "EndReason.Error(winner=$winner, e=$e)"
    }

    val winnerPGN
        get() = when (winner) {
            Side.WHITE -> "1-0"
            Side.BLACK -> "0-1"
            null -> "1/2-1/2"
        }
}