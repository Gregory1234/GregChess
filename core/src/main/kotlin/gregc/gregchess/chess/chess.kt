package gregc.gregchess.chess

enum class Side(val char: Char, val direction: Int) {
    WHITE('w', 1),
    BLACK('b', -1);

    operator fun not(): Side = if (this == WHITE) BLACK else WHITE
    operator fun inc(): Side = not()

    val dir get() = Dir(0, direction)

    companion object {
        fun parseFromChar(c: Char) =
            values().firstOrNull { it.char == c }
                ?: throw IllegalArgumentException(c.toString())

        inline fun forEach(block: (Side) -> Unit) = values().forEach(block)
    }

}

val white get() = Side.WHITE
val black get() = Side.BLACK

enum class BoardSide(private val direction: Int, val castles: String) {
    QUEENSIDE(-1, "O-O-O"), KINGSIDE(1, "O-O");

    val dir get() = Dir(direction, 0)
}

val kingside get() = BoardSide.KINGSIDE
val queenside get() = BoardSide.KINGSIDE

data class MutableBySides<T>(var white: T, var black: T) {
    constructor(block: (Side) -> T) : this(block(Side.WHITE), block(Side.BLACK))
    constructor(v: T) : this(v, v)

    operator fun get(side: Side) = when (side) {
        Side.WHITE -> white
        Side.BLACK -> black
    }

    operator fun set(side: Side, value: T) = when (side) {
        Side.WHITE -> {
            white = value
        }
        Side.BLACK -> {
            black = value
        }
    }

    fun toList(): List<T> = listOf(white, black)
    inline fun forEach(f: (T) -> Unit) {
        f(white)
        f(black)
    }

    inline fun <R> forEachIndexed(f: (Side, T) -> R) {
        f(Side.WHITE, white)
        f(Side.BLACK, black)
    }

    inline fun <R> map(crossinline f: (T) -> R) = BySides { f(this[it]) }
    inline fun <R> mapIndexed(crossinline f: (Side, T) -> R) = BySides { f(it, this[it]) }
    fun toBySides() = BySides(white, black)
}

data class BySides<T>(val white: T, val black: T) {
    constructor(block: (Side) -> T) : this(block(Side.WHITE), block(Side.BLACK))
    constructor(v: T) : this(v, v)

    operator fun get(side: Side) = when (side) {
        Side.WHITE -> white
        Side.BLACK -> black
    }

    fun toList(): List<T> = listOf(white, black)
    inline fun forEach(f: (T) -> Unit) {
        f(white)
        f(black)
    }

    inline fun <R> forEachIndexed(f: (Side, T) -> R) {
        f(Side.WHITE, white)
        f(Side.BLACK, black)
    }

    inline fun <R> map(crossinline f: (T) -> R) = BySides { f(this[it]) }
    inline fun <R> mapIndexed(crossinline f: (Side, T) -> R) = BySides { f(it, this[it]) }
    fun toMutableBySides() = MutableBySides(white, black)
}

class NoEngineMoveException(fen: FEN) : Exception(fen.toString())

interface ChessEngine {
    val name: String
    fun stop()
    fun setOption(name: String, value: String)
    fun sendCommand(command: String)
    suspend fun getMove(fen: FEN): String
}