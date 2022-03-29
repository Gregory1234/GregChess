package gregc.gregchess.util

import gregc.gregchess.move.Move

enum class BoardSide(private val direction: Int, val castles: String) {
    QUEENSIDE(-1, "O-O-O"), KINGSIDE(1, "O-O");

    val dir get() = Dir(direction, 0)
}

fun interface MoveFormatter {
    fun format(move: Move): String
}