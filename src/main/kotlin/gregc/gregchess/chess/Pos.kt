package gregc.gregchess.chess

import gregc.gregchess.rangeTo
import gregc.gregchess.Config

data class Pos(val file: Int, val rank: Int) {
    override fun toString() = "$fileStr$rankStr"
    operator fun plus(diff: Pair<Int, Int>) = plus(diff.first, diff.second)
    fun plus(df: Int, dr: Int) = Pos(file + df, rank + dr)
    fun plusF(df: Int) = plus(df, 0)
    fun plusR(dr: Int) = plus(0, dr)
    val fileStr = "${'a' + file}"
    val rankStr = (rank + 1).toString()

    fun neighbours(): List<Pos> =
        (Pair(-1, -1)..Pair(1, 1)).map(::plus).filter { it.isValid() } - this

    fun isValid() = file in (0..7) && rank in (0..7)

    companion object {
        fun parseFromString(s: String): Pos {
            if (s.length != 2 || s[0] !in 'a'..'h' || s[1] !in '1'..'8')
                throw IllegalArgumentException(s)
            return Pos(s[0].lowercaseChar() - 'a', s[1] - '1')
        }
    }
}

class PosSteps(val start: Pos, private val jump: Pair<Int, Int>, override val size: Int) : Collection<Pos> {
    class PosIterator(val start: Pos, private val jump: Pair<Int, Int>, private var remaining: Int) : Iterator<Pos> {
        private var value = start

        override fun hasNext() = remaining > 0

        override fun next(): Pos {
            val ret = value
            value += jump
            remaining--
            return ret
        }
    }

    companion object {
        private fun calcSize(start: Pos, jump: Pair<Int, Int>): Int {
            val ret = mutableListOf<Int>()

            if (jump.first > 0) {
                ret += (8 - start.file).floorDiv(jump.first)
            } else if (jump.first < 0) {
                ret += (start.file + 1).floorDiv(-jump.first)
            }

            if (jump.second > 0) {
                ret += (8 - start.rank).floorDiv(jump.second)
            } else if (jump.second < 0) {
                ret += (start.rank + 1).floorDiv(-jump.second)
            }

            return ret.minOrNull() ?: 1
        }
    }

    constructor(start: Pos, jump: Pair<Int, Int>) : this(start, jump, calcSize(start, jump))

    override fun contains(element: Pos): Boolean {
        if (jump.first == 0) {
            if (element.file != start.file)
                return false
            if (jump.second == 0)
                return element == start
            val m = (element.rank - start.rank).mod(jump.second)
            if (m != 0)
                return false
            val d = (element.rank - start.rank).floorDiv(jump.second)
            return d in (0 until size)
        } else {
            val m = (element.file - start.file).mod(jump.first)
            if (m != 0)
                return false
            val d = (element.file - start.file).floorDiv(jump.first)
            if (d !in (0 until size))
                return false
            return (element.rank - start.rank) == d * jump.second
        }
    }

    override fun containsAll(elements: Collection<Pos>) = elements.all { it in this }

    override fun isEmpty() = size <= 0

    override fun iterator() = PosIterator(start, jump, size)
}

enum class Floor {
    LIGHT, DARK, MOVE, CAPTURE, SPECIAL, NOTHING, OTHER, LAST_START, LAST_END;
    val material get() = Config.Chess.Floor[this]
}

data class Square(val pos: Pos, val game: ChessGame) {
    var piece: BoardPiece? = null
    var bakedMoves: List<MoveCandidate>? = null
    var bakedLegalMoves: List<MoveCandidate>? = null

    private var noRender = false

    private val baseFloor = if ((pos.file + pos.rank) % 2 == 0) Floor.DARK else Floor.LIGHT
    var variantMarker: Floor? = null
        set(v) {
            field = v
            if (!noRender) render()
        }
    var previousMoveMarker: Floor? = null
        set(v) {
            field = v
            if (!noRender) render()
        }
    var moveMarker: Floor? = null
        set(v) {
            field = v
            if (!noRender) render()
        }
    private val floor
        get() = moveMarker ?: previousMoveMarker ?: variantMarker ?: baseFloor

    val board
        get() = game.board

    override fun toString() = "Square(game.uniqueId=${game.uniqueId}, pos=$pos, piece=$piece, floor=$floor)"

    fun render() {
        game.renderers.forEach { it.fillFloor(pos, floor) }
    }

    fun empty() {
        piece = null
        bakedMoves = null
        noRender = true
        variantMarker = null
        previousMoveMarker = null
        moveMarker = null
        noRender = false
    }

    fun clear() {
        piece?.clear()
        empty()
        render()
    }

    fun neighbours() = pos.neighbours().mapNotNull { this.board[it] }
}
