package gregc.gregchess.chess

sealed class GameScore(val pgn: String) {
    class Victory(val winner: Side): GameScore(when(winner) {Side.WHITE -> "1-0"; Side.BLACK -> "0-1"})
    object Draw: GameScore("1/2-1/2")
}

typealias DetEndReason = EndReason<GameScore.Victory>
typealias DrawEndReason = EndReason<GameScore.Draw>

class EndReason<R: GameScore>(val name: String, val type: Type, val quick: Boolean = false) {

    enum class Type(val pgn: String) {
        NORMAL("normal"), ABANDONED("abandoned"), TIME_FORFEIT("time forfeit"), EMERGENCY("emergency")
    }



    override fun toString() = name

    companion object{
        @JvmField
        val CHECKMATE = DetEndReason("CHECKMATE", Type.NORMAL)
        @JvmField
        val RESIGNATION = DetEndReason("RESIGNATION", Type.ABANDONED)
        @JvmField
        val WALKOVER = DetEndReason("WALKOVER", Type.ABANDONED)
        @JvmField
        val STALEMATE = DrawEndReason("STALEMATE", Type.NORMAL)
        @JvmField
        val INSUFFICIENT_MATERIAL = DrawEndReason("INSUFFICIENT_MATERIAL", Type.NORMAL)
        @JvmField
        val FIFTY_MOVES = DrawEndReason("FIFTY_MOVES", Type.NORMAL)
        @JvmField
        val REPETITION = DrawEndReason("REPETITION", Type.NORMAL)
        @JvmField
        val DRAW_AGREEMENT = DrawEndReason("DRAW_AGREEMENT", Type.NORMAL)
        @JvmField
        val TIMEOUT = DetEndReason("TIMEOUT", Type.TIME_FORFEIT)
        @JvmField
        val DRAW_TIMEOUT = DrawEndReason("DRAW_TIMEOUT", Type.TIME_FORFEIT)
        @JvmField
        val ALL_PIECES_LOST = DetEndReason("ALL_PIECES_LOST", Type.NORMAL)
        @JvmField
        val ERROR = DrawEndReason("ERROR", Type.EMERGENCY)
    }

    val pgn get() = type.pgn
}

fun Side.wonBy(reason: DetEndReason, vararg args: Any?) = GameResults(reason, GameScore.Victory(this), args.toList())
fun Side.lostBy(reason: DetEndReason, vararg args: Any?) = (!this).wonBy(reason, *args)
fun drawBy(reason: DrawEndReason, vararg args: Any?) = GameResults(reason, GameScore.Draw, args.toList())
fun DrawEndReason.of(vararg args: Any?) = GameResults(this, GameScore.Draw, args.toList())

data class GameResults<R: GameScore>(val endReason: EndReason<R>, val score: R, val args: List<Any?>)