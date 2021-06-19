package gregc.gregchess.chess

import gregc.gregchess.*
import kotlin.reflect.KProperty1

open class EndReason(
    val namePath: ConfigVal<String>, val reasonPGN: String, val winner: Side? = null, val quick: Boolean = false
) {

    constructor(
        namePath: KProperty1<EndReasonConfig, String>, reasonPGN: String, winner: Side? = null, quick: Boolean = false
    ) : this(namePath.path, reasonPGN, winner, quick)

    override fun toString() = "EndReason.${javaClass.name.split(".", "$").last()}(winner=$winner)"

    class Checkmate(winner: Side) : EndReason(EndReasonConfig::checkmate, "normal", winner)
    class Resignation(winner: Side) : EndReason(EndReasonConfig::resignation, "abandoned", winner)
    class Walkover(winner: Side) : EndReason(EndReasonConfig::walkover, "abandoned", winner)
    class Stalemate : EndReason(EndReasonConfig::stalemate, "normal")
    class InsufficientMaterial : EndReason(EndReasonConfig::insufficientMaterial, "normal")
    class FiftyMoves : EndReason(EndReasonConfig::fiftyMoves, "normal")
    class Repetition : EndReason(EndReasonConfig::repetition, "normal")
    class DrawAgreement : EndReason(EndReasonConfig::drawAgreement, "normal")
    class Timeout(winner: Side) : EndReason(EndReasonConfig::timeout, "time forfeit", winner)
    class DrawTimeout : EndReason(EndReasonConfig::drawTimeout, "time forfeit")
    class AllPiecesLost(winner: Side) : EndReason(EndReasonConfig::piecesLost, "normal", winner)
    class Error(val e: Exception) : EndReason(EndReasonConfig::error, "emergency") {
        override fun toString() = "EndReason.Error(winner=$winner, e=$e)"
    }

    val message
        get() = with(Config.message) {
            winner?.let { gameFinished(namePath.get())[it] } ?: gameFinishedDraw(namePath.get())
        }

    val winnerPGN
        get() = when (winner) {
            Side.WHITE -> "1-0"
            Side.BLACK -> "0-1"
            null -> "1/2-1/2"
        }
}