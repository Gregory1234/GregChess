package gregc.gregchess.chess

import gregc.gregchess.snakeToPascal

enum class Side(val standardChar: Char, val direction: Int) {
    WHITE('w', 1),
    BLACK('b', -1);

    val standardName = name.snakeToPascal()

    operator fun not(): Side = if (this == WHITE) BLACK else WHITE
    operator fun inc(): Side = not()

    companion object {
        fun parseFromStandardChar(c: Char) =
            values().firstOrNull { it.standardChar == c }
                ?: throw IllegalArgumentException(c.toString())
    }

}

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

interface ChessEngine {
    val name: String
    fun stop()
    fun setOption(name: String, value: String)
    fun sendCommand(command: String)
    fun getMove(fen: FEN, onSuccess: (String) -> Unit, onException: (Exception) -> Unit)
}