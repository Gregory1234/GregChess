package gregc.gregchess.move

fun interface MoveFormatter {
    fun format(move: Move): String
}