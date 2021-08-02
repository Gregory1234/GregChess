package gregc.gregchess.chess

import gregc.gregchess.GregChessModule

private fun victoryPgn(winner: Side) = when(winner) {
    Side.WHITE -> "1-0"
    Side.BLACK -> "0-1"
}

sealed class GameScore(val pgn: String) {
    class Victory(val winner: Side): GameScore(victoryPgn(winner))
    object Draw: GameScore("1/2-1/2")
}

typealias DetEndReason = EndReason<GameScore.Victory>
typealias DrawEndReason = EndReason<GameScore.Draw>

class EndReason<R : GameScore>(val name: String, val type: Type, val quick: Boolean = false) {

    enum class Type(val pgn: String) {
        NORMAL("normal"), ABANDONED("abandoned"), TIME_FORFEIT("time forfeit"), EMERGENCY("emergency")
    }

    override fun toString() = name

    companion object {
        @JvmField
        val CHECKMATE = GregChessModule.register(DetEndReason("CHECKMATE", Type.NORMAL))
        @JvmField
        val RESIGNATION = GregChessModule.register(DetEndReason("RESIGNATION", Type.ABANDONED))
        @JvmField
        val WALKOVER = GregChessModule.register(DetEndReason("WALKOVER", Type.ABANDONED))
        @JvmField
        val STALEMATE = GregChessModule.register(DrawEndReason("STALEMATE", Type.NORMAL))
        @JvmField
        val INSUFFICIENT_MATERIAL = GregChessModule.register(DrawEndReason("INSUFFICIENT_MATERIAL", Type.NORMAL))
        @JvmField
        val FIFTY_MOVES = GregChessModule.register(DrawEndReason("FIFTY_MOVES", Type.NORMAL))
        @JvmField
        val REPETITION = GregChessModule.register(DrawEndReason("REPETITION", Type.NORMAL))
        @JvmField
        val DRAW_AGREEMENT = GregChessModule.register(DrawEndReason("DRAW_AGREEMENT", Type.NORMAL))
        @JvmField
        val TIMEOUT = GregChessModule.register(DetEndReason("TIMEOUT", Type.TIME_FORFEIT))
        @JvmField
        val DRAW_TIMEOUT = GregChessModule.register(DrawEndReason("DRAW_TIMEOUT", Type.TIME_FORFEIT))
        @JvmField
        val ALL_PIECES_LOST = GregChessModule.register(DetEndReason("ALL_PIECES_LOST", Type.NORMAL))
        @JvmField
        val ERROR = GregChessModule.register(DrawEndReason("ERROR", Type.EMERGENCY))
    }

    val pgn get() = type.pgn
}

fun Side.wonBy(reason: DetEndReason, vararg args: Any?) = GameResults(reason, GameScore.Victory(this), args.toList())
fun Side.lostBy(reason: DetEndReason, vararg args: Any?) = (!this).wonBy(reason, *args)
fun drawBy(reason: DrawEndReason, vararg args: Any?) = GameResults(reason, GameScore.Draw, args.toList())
fun DrawEndReason.of(vararg args: Any?) = GameResults(this, GameScore.Draw, args.toList())

data class GameResults<R : GameScore>(val endReason: EndReason<R>, val score: R, val args: List<Any?>)