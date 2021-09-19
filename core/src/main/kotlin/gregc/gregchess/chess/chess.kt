package gregc.gregchess.chess

import kotlinx.serialization.Serializable

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

@Serializable
data class MutableBySides<T> internal constructor(var white: T, var black: T) {
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
    fun toIndexedList(): List<Pair<Side, T>> = listOf(Side.WHITE to white, Side.BLACK to black)
    inline fun forEach(f: (T) -> Unit) {
        f(white)
        f(black)
    }
}

fun <T> mutableBySides(white: T, black: T) = MutableBySides(white, black)
fun <T> mutableBySides(both: T) = MutableBySides(both, both)
inline fun <T> mutableBySides(block: (Side) -> T) = mutableBySides(block(Side.WHITE), block(Side.BLACK))

@Serializable
data class BySides<out T> internal constructor(val white: T, val black: T) {

    operator fun get(side: Side) = when (side) {
        Side.WHITE -> white
        Side.BLACK -> black
    }

    fun toList(): List<T> = listOf(white, black)
    fun toIndexedList(): List<Pair<Side, T>> = listOf(Side.WHITE to white, Side.BLACK to black)
    inline fun forEach(f: (T) -> Unit) {
        f(white)
        f(black)
    }
}

fun <T> bySides(white: T, black: T) = BySides(white, black)
fun <T> bySides(both: T) = BySides(both, both)
inline fun <T> bySides(block: (Side) -> T) = bySides(block(Side.WHITE), block(Side.BLACK))

class NoEngineMoveException(fen: FEN) : Exception(fen.toString())



interface ChessEngine: ChessPlayerInfo {
    fun stop()
    fun setOption(name: String, value: String)
    fun sendCommand(command: String)
    suspend fun getMove(fen: FEN): String
    override fun getPlayer(side: Side, game: ChessGame) = EnginePlayer(this, side, game)
}