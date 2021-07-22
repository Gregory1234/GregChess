package gregc.gregchess.chess

import gregc.gregchess.Identifier
import gregc.gregchess.asIdent

sealed class EndResults(val pgn: String) {
    class Victory(val winner: Side): EndResults(when(winner) {Side.WHITE -> "1-0"; Side.BLACK -> "0-1"})
    object Draw: EndResults("1/2-1/2")
}

typealias DetEndReason = EndReason<EndResults.Victory>
typealias DrawEndReason = EndReason<EndResults.Draw>

open class EndReason<R: EndResults>(val id: Identifier, val type: Type, val quick: Boolean = false) {

    enum class Type(val pgn: String) {
        NORMAL("normal"), ABANDONED("abandoned"), TIME_FORFEIT("time forfeit"), EMERGENCY("emergency")
    }



    override fun toString() = id.toString()

    companion object{
        @JvmField
        val CHECKMATE = DetEndReason("checkmate".asIdent(), Type.NORMAL)
        @JvmField
        val RESIGNATION = DetEndReason("resignation".asIdent(), Type.ABANDONED)
        @JvmField
        val WALKOVER = DetEndReason("walkover".asIdent(), Type.ABANDONED)
        @JvmField
        val STALEMATE = DrawEndReason("stalemate".asIdent(), Type.NORMAL)
        @JvmField
        val INSUFFICIENT_MATERIAL = DrawEndReason("insufficient_material".asIdent(), Type.NORMAL)
        @JvmField
        val FIFTY_MOVES = DrawEndReason("fifty_moves".asIdent(), Type.NORMAL)
        @JvmField
        val REPETITION = DrawEndReason("repetition".asIdent(), Type.NORMAL)
        @JvmField
        val DRAW_AGREEMENT = DrawEndReason("draw_agreement".asIdent(), Type.NORMAL)
        @JvmField
        val TIMEOUT = DetEndReason("timeout".asIdent(), Type.TIME_FORFEIT)
        @JvmField
        val DRAW_TIMEOUT = DrawEndReason("draw_timeout".asIdent(), Type.TIME_FORFEIT)
        @JvmField
        val ALL_PIECES_LOST = DetEndReason("pieces_lost".asIdent(), Type.NORMAL)
        @JvmField
        val ERROR = DrawEndReason("error".asIdent(), Type.EMERGENCY)
    }

    val pgn get() = type.pgn
}

fun DetEndReason.of(winner: Side, vararg args: Any?) = GameEnd(this, EndResults.Victory(winner), args.toList())
fun DrawEndReason.of(vararg args: Any?) = GameEnd(this, EndResults.Draw, args.toList())

data class GameEnd<R: EndResults>(val reason: EndReason<R>, val result: R, val args: List<Any?>) {

}