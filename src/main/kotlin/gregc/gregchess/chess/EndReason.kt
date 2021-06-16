package gregc.gregchess.chess

import gregc.gregchess.*
import kotlin.reflect.KProperty1

open class EndReason(val namePath: ConfigVal<String>, val reasonPGN: String, val winner: Side? = null) {

    constructor(namePath: KProperty1<EndReasonConfig, String>, reasonPGN: String, winner: Side? = null)
            : this(namePath.path, reasonPGN, winner)

    override fun toString() = "EndReason.${javaClass.name.split(".", "$").last()}(winner=$winner)"

    class Checkmate(winner: Side) : EndReason(EndReasonConfig::checkmate, "normal", winner)
    class Resignation(winner: Side) : EndReason(EndReasonConfig::resignation, "abandoned", winner)
    class Walkover(winner: Side) : EndReason(EndReasonConfig::walkover, "abandoned", winner)
    class PluginRestart : EndReason(EndReasonConfig::pluginRestart, "emergency")
    class ArenaRemoved : EndReason(EndReasonConfig::arenaRemoved, "emergency")
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

    val message get() = with(Config.message) { winner?.let { gameFinished[it] } ?: gameFinishedDraw }(namePath.get())
}