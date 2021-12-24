package gregc.gregchess.chess.move

import gregc.gregchess.chess.component.Chessboard
import gregc.gregchess.chess.piece.PlacedPiece
import gregc.gregchess.chess.piece.multiMove
import kotlinx.serialization.Serializable

@Serializable
class PieceTracker private constructor(
    private val pieces: MutableMap<String, MutableList<PlacedPiece>>,
    private val synonyms: MutableMap<String, String> = mutableMapOf()
) {

    constructor(vararg pieces: Pair<String, PlacedPiece>)
            : this(mapOf(*pieces).mapValues { mutableListOf(it.value) }.toMutableMap())

    constructor(piece: PlacedPiece) : this("main" to piece)

    fun getOrNull(name: String) = (pieces[synonyms[name] ?: name])?.lastOrNull()
    operator fun get(name: String) = getOrNull(name)!!

    private fun addMoves(vararg moves: Pair<PlacedPiece, PlacedPiece>) {
        moves.map { m -> pieces.entries.single { it.value.last() == m.first }.value to m.second }
            .forEach { m -> m.first += m.second }
    }

    private fun addMovesBack(vararg moves: Pair<PlacedPiece, PlacedPiece>) {
        moves.map { m -> pieces.entries.single { it.value.last() == m.first }.value }.forEach { it.removeLast() }
    }

    fun giveName(name: String, piece: PlacedPiece) {
        if (name in pieces || name in synonyms) {
            require(piece == get(name))
            return
        }
        val candidate = pieces.entries.singleOrNull { it.value.last() == piece }
        if (candidate != null) {
            synonyms[name] = candidate.key
        } else {
            pieces[name] = mutableListOf(piece)
        }
    }

    fun getOriginal(name: String): PlacedPiece = pieces[synonyms[name] ?: name]!!.first()

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