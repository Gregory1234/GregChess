package gregc.gregchess.chess.move

import gregc.gregchess.chess.piece.*
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

    fun getOriginalOrNull(name: String): PlacedPiece? = pieces[synonyms[name] ?: name]?.firstOrNull()

    fun getOriginal(name: String): PlacedPiece = pieces[synonyms[name] ?: name]!!.first()

    private fun traceBack(piece: PlacedPiece): PlacedPiece = pieces.entries.single { it.value.last() == piece }.value.let {
        it[it.size-2]
    }

    fun traceMoveBack(holder: PieceHolder<PlacedPiece>, vararg pieces: PlacedPiece) {
        val revMoves = pieces.map { Pair(it, traceBack(it)) }.toTypedArray()
        multiMove(holder, *revMoves)
        addMovesBack(*revMoves)
    }

    fun traceMove(holder: PieceHolder<PlacedPiece>, vararg moves: Pair<PlacedPiece, PlacedPiece>) {
        multiMove(holder, *moves)
        addMoves(*moves)
    }

}