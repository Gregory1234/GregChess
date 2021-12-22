package gregc.gregchess.chess.move

import gregc.gregchess.chess.component.Chessboard
import gregc.gregchess.chess.piece.PlacedPiece
import gregc.gregchess.chess.piece.multiMove
import kotlinx.serialization.Serializable

// TODO: make the serialized version nicer
// TODO: replace string names with a "move role" class
// TODO: don't require having all relevant pieces named from the start
@Serializable
class PieceTracker private constructor(private val pieces: Map<String, MutableList<PlacedPiece>>) {
    constructor(vararg pieces: Pair<String, PlacedPiece?>) : this(mapOf(*pieces).filterValues { it != null }.mapValues { mutableListOf(it.value!!) })
    constructor(piece: PlacedPiece, capture: PlacedPiece? = null) : this("main" to piece, "capture" to capture)

    fun getOrNull(name: String) = pieces[name]?.lastOrNull()
    operator fun get(name: String) = getOrNull(name)!!

    fun move(origin: PlacedPiece, target: PlacedPiece) {
        require(pieces.none { it.value.last() == target && it.value.last() != origin })
        pieces.entries.single { it.value.last() == origin }.value += target
    }

    fun move(name: String, target: PlacedPiece) {
        require(pieces.none { it.value.last() == target && it.key != name })
        pieces[name]!! += target
    }

    fun getOriginal(name: String): PlacedPiece = pieces[name]!!.first()

    private fun traceBack(piece: PlacedPiece): PlacedPiece = pieces.entries.single { it.value.last() == piece }.value.let {
        it[it.size-2]
    }

    fun traceMoveBack(board: Chessboard, vararg pieces: PlacedPiece) =
        traceMove(board, *pieces.map { Pair(it, traceBack(it)) }.toTypedArray())

    fun traceMove(board: Chessboard, vararg moves: Pair<PlacedPiece, PlacedPiece>) {
        for ((o,t) in moves)
            move(o,t)
        multiMove(board, *moves)
    }

}