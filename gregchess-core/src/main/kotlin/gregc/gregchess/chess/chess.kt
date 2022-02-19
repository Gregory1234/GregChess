package gregc.gregchess.chess

import gregc.gregchess.chess.move.Move
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.Serializable

enum class Color(val char: Char, private val direction: Int) {
    WHITE('w', 1),
    BLACK('b', -1);

    operator fun not(): Color = if (this == WHITE) BLACK else WHITE
    operator fun inc(): Color = not()

    val forward get() = Dir(0, direction)

    companion object {
        fun parseFromChar(c: Char) =
            requireNotNull(values().firstOrNull { it.char == c }) { "'$c' is not valid color character" }

        inline fun forEach(block: (Color) -> Unit) = values().forEach(block)
    }

}

enum class BoardSide(private val direction: Int, val castles: String) {
    QUEENSIDE(-1, "O-O-O"), KINGSIDE(1, "O-O");

    val dir get() = Dir(direction, 0)
}

@Serializable
data class MutableByColor<T> internal constructor(var white: T, var black: T) {
    operator fun get(color: Color) = when (color) {
        Color.WHITE -> white
        Color.BLACK -> black
    }

    operator fun set(color: Color, value: T) = when (color) {
        Color.WHITE -> white = value
        Color.BLACK -> black = value
    }

    fun toList(): List<T> = listOf(white, black)
    fun toIndexedList(): List<Pair<Color, T>> = listOf(Color.WHITE to white, Color.BLACK to black)
    inline fun forEach(f: (T) -> Unit) {
        f(white)
        f(black)
    }
}

fun <T> mutableByColor(white: T, black: T) = MutableByColor(white, black)
fun <T> mutableByColor(both: T) = MutableByColor(both, both)
inline fun <T> mutableByColor(block: (Color) -> T) = mutableByColor(block(Color.WHITE), block(Color.BLACK))

@Serializable
data class ByColor<out T> internal constructor(val white: T, val black: T) {

    operator fun get(color: Color) = when (color) {
        Color.WHITE -> white
        Color.BLACK -> black
    }

    fun toList(): List<T> = listOf(white, black)
    fun toIndexedList(): List<Pair<Color, T>> = listOf(Color.WHITE to white, Color.BLACK to black)
    inline fun forEach(f: (T) -> Unit) {
        f(white)
        f(black)
    }
}

fun <T> byColor(white: T, black: T) = ByColor(white, black)
fun <T> byColor(both: T) = ByColor(both, both)
inline fun <T> byColor(block: (Color) -> T) = byColor(block(Color.WHITE), block(Color.BLACK))

interface ChessEnvironment {
    val pgnSite: String
    val coroutineDispatcher: CoroutineDispatcher
}

fun interface MoveFormatter {
    fun format(move: Move): String
}