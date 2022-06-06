package gregc.gregchess.move

fun interface MoveFormatter {
    fun format(move: Move): String
}

class SimpleMoveFormatterContext {
    private val builder: StringBuilder = StringBuilder()
    operator fun Any?.unaryPlus() { if (this != null) builder.append(this) }
    operator fun String?.unaryPlus() { if (this != null) builder.append(this) }
    override fun toString(): String = builder.toString()
}

fun simpleMoveFormatter(formatter: SimpleMoveFormatterContext.(Move) -> Unit): MoveFormatter = MoveFormatter {
    val ctx = SimpleMoveFormatterContext()
    ctx.formatter(it)
    ctx.toString()
}