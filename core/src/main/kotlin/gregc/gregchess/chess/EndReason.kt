package gregc.gregchess.chess

import gregc.gregchess.*

open class EndReason(
    val name: LocalizedString, val reasonPGN: String, val winner: Side? = null, val quick: Boolean = false
) {

    override fun toString() = "EndReason.${javaClass.name.split(".", "$").last()}(winner=$winner)"

    class Checkmate(winner: Side) : EndReason(Config.endReason.checkmate, "normal", winner)
    class Resignation(winner: Side) : EndReason(Config.endReason.resignation, "abandoned", winner)
    class Walkover(winner: Side) : EndReason(Config.endReason.walkover, "abandoned", winner)
    class Stalemate : EndReason(Config.endReason.stalemate, "normal")
    class InsufficientMaterial : EndReason(Config.endReason.insufficientMaterial, "normal")
    class FiftyMoves : EndReason(Config.endReason.fiftyMoves, "normal")
    class Repetition : EndReason(Config.endReason.repetition, "normal")
    class DrawAgreement : EndReason(Config.endReason.drawAgreement, "normal")
    class Timeout(winner: Side) : EndReason(Config.endReason.timeout, "time forfeit", winner)
    class DrawTimeout : EndReason(Config.endReason.drawTimeout, "time forfeit")
    class AllPiecesLost(winner: Side) : EndReason(Config.endReason.piecesLost, "normal", winner)
    class Error(val e: Exception) : EndReason(Config.endReason.error, "emergency") {
        override fun toString() = "EndReason.Error(winner=$winner, e=$e)"
    }

    val message
        get() = LocalizedString { lang ->
            winner?.let { Config.message.gameFinished(name.get(lang))[it].get(lang) }
                ?: Config.message.gameFinishedDraw(name.get(lang)).get(lang)
        }

    val winnerPGN
        get() = when (winner) {
            Side.WHITE -> "1-0"
            Side.BLACK -> "0-1"
            null -> "1/2-1/2"
        }
}