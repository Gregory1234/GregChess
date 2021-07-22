package gregc.gregchess.chess

import gregc.gregchess.Identifier
import gregc.gregchess.asIdent

sealed class GameScore(val pgn: String) {
    class Victory(val winner: Side): GameScore(when(winner) {Side.WHITE -> "1-0"; Side.BLACK -> "0-1"})
    object Draw: GameScore("1/2-1/2")
}

typealias DetEndReason = EndReason<GameScore.Victory>
typealias DrawEndReason = EndReason<GameScore.Draw>

open class EndReason<R: GameScore>(val id: Identifier, val type: Type, val quick: Boolean = false) {

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

fun Side.wonBy(reason: DetEndReason, vararg args: Any?) = GameResults(reason, GameScore.Victory(this), args.toList())
fun Side.lostBy(reason: DetEndReason, vararg args: Any?) = (!this).wonBy(reason, *args)
fun drawBy(reason: DrawEndReason, vararg args: Any?) = GameResults(reason, GameScore.Draw, args.toList())
fun DrawEndReason.of(vararg args: Any?) = GameResults(this, GameScore.Draw, args.toList())

data class GameResults<R: GameScore>(val endReason: EndReason<R>, val score: R, val args: List<Any?>) {

}