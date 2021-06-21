package gregc.gregchess.chess

import gregc.gregchess.*

fun MessageConfig.gameFinished(w: Side?, a: String) =
    getMessage("GameFinished." + (w?.standardName?.plus("Won") ?: "ItWasADraw"), a)

val EndReasonConfig.checkmate by EndReasonConfig
val EndReasonConfig.resignation by EndReasonConfig
val EndReasonConfig.walkover by EndReasonConfig
val EndReasonConfig.stalemate by EndReasonConfig
val EndReasonConfig.insufficientMaterial by EndReasonConfig
val EndReasonConfig.fiftyMoves by EndReasonConfig
val EndReasonConfig.repetition by EndReasonConfig
val EndReasonConfig.drawAgreement by EndReasonConfig
val EndReasonConfig.timeout by EndReasonConfig
val EndReasonConfig.drawTimeout by EndReasonConfig
val EndReasonConfig.piecesLost by EndReasonConfig
val EndReasonConfig.error by EndReasonConfig

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
        get() = Localized { Config.message.gameFinished(winner, name.get(it)).get(it) }

    val winnerPGN
        get() = when (winner) {
            Side.WHITE -> "1-0"
            Side.BLACK -> "0-1"
            null -> "1/2-1/2"
        }
}