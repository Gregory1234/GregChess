package gregc.gregchess.chess

import kotlinx.serialization.Serializable

enum class Color(val char: Char, private val direction: Int) {
    WHITE('w', 1),
    BLACK('b', -1);

    operator fun not(): Color = if (this == WHITE) BLACK else WHITE
    operator fun inc(): Color = not()

    val forward get() = Dir(0, direction)

    companion object {
        fun parseFromChar(c: Char) =
            values().firstOrNull { it.char == c }
                ?: throw IllegalArgumentException(c.toString())

        inline fun forEach(block: (Color) -> Unit) = values().forEach(block)
    }

}

enum class BoardSide(private val direction: Int, val castles: String) {
    QUEENSIDE(-1, "O-O-O"), KINGSIDE(1, "O-O");

    val dir get() = Dir(direction, 0)
}

@Serializable
data class MutableBySides<T> internal constructor(var white: T, var black: T) {
    operator fun get(color: Color) = when (color) {
        Color.WHITE -> white
        Color.BLACK -> black
    }

    operator fun set(color: Color, value: T) = when (color) {
        Color.WHITE -> {
            white = value
        }
        Color.BLACK -> {
            black = value
        }
    }

    fun toList(): List<T> = listOf(white, black)
    fun toIndexedList(): List<Pair<Color, T>> = listOf(Color.WHITE to white, Color.BLACK to black)
    inline fun forEach(f: (T) -> Unit) {
        f(white)
        f(black)
    }
}

fun <T> mutableBySides(white: T, black: T) = MutableBySides(white, black)
fun <T> mutableBySides(both: T) = MutableBySides(both, both)
inline fun <T> mutableBySides(block: (Color) -> T) = mutableBySides(block(Color.WHITE), block(Color.BLACK))

@Serializable
data class BySides<out T> internal constructor(val white: T, val black: T) {

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

fun <T> bySides(white: T, black: T) = BySides(white, black)
fun <T> bySides(both: T) = BySides(both, both)
inline fun <T> bySides(block: (Color) -> T) = bySides(block(Color.WHITE), block(Color.BLACK))

class NoEngineMoveException(fen: FEN) : Exception(fen.toString())



interface ChessEngine: ChessPlayerInfo {
    fun stop()
    fun setOption(name: String, value: String)
    fun sendCommand(command: String)
    suspend fun getMove(fen: FEN): String
    override fun getPlayer(color: Color, game: ChessGame) = EnginePlayer(this, color, game)
}