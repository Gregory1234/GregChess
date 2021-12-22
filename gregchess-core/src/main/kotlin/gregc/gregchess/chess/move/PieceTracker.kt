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

    private fun addMoves(vararg moves: Pair<PlacedPiece, PlacedPiece>) {
        moves.map { m -> pieces.entries.single { it.value.last() == m.first }.value to m.second }
            .forEach { m -> m.first += m.second }
    }

    private fun addMovesBack(vararg moves: Pair<PlacedPiece, PlacedPiece>) {
        moves.map { m -> pieces.entries.single { it.value.last() == m.first }.value }.forEach { it.removeLast() }
    }

    fun getOriginal(name: String): PlacedPiece = pieces[name]!!.first()

    private fun traceBack(piece: PlacedPiece): PlacedPiece = pieces.entries.single { it.value.last() == piece }.value.let {
        it[it.size-2]
    }

    fun traceMoveBack(board: Chessboard, vararg pieces: PlacedPiece) {
        val revMoves = pieces.map { Pair(it, traceBack(it)) }.toTypedArray()
        addMovesBack(*revMoves)
        multiMove(board, *revMoves)
    }

    fun traceMove(board: Chessboard, vararg moves: Pair<PlacedPiece, PlacedPiece>) {
        addMoves(*moves)
        multiMove(board, *moves)
    }

}