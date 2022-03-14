package gregc.gregchess.chess.move

import gregc.gregchess.chess.piece.*
import kotlinx.serialization.Serializable

@Serializable
class PieceTracker private constructor(
    private val pieces: MutableMap<String, MutableList<PlacedPiece>> = mutableMapOf(),
    private val synonyms: MutableMap<String, String> = mutableMapOf()
) {

    constructor(vararg pieces: Pair<String, PlacedPiece>) : this() {
        for ((n, p) in pieces) {
            giveName(n, p)
        }
    }

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
        val candidate = pieces.entries.singleOrNull { it.value.last() conflictsWith piece }
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

    fun traceMoveBack(holder: PieceHolder<PlacedPiece>, pieceEventCaller: PieceEventCaller, vararg pieces: PlacedPiece) {
        val revMoves = pieces.map { Pair(it, traceBack(it)) }.toTypedArray()
        holder.multiMove(pieceEventCaller, *revMoves)
        addMovesBack(*revMoves)
    }

    fun <T> traceMoveBack(holder: T, vararg pieces: PlacedPiece) where T : PieceHolder<PlacedPiece>, T : PieceEventCaller = traceMoveBack(holder, holder, *pieces)

    fun traceMove(holder: PieceHolder<PlacedPiece>, pieceEventCaller: PieceEventCaller, vararg moves: Pair<PlacedPiece, PlacedPiece>) {
        holder.multiMove(pieceEventCaller, *moves)
        addMoves(*moves)
    }

    fun <T> traceMove(holder: T, vararg moves: Pair<PlacedPiece, PlacedPiece>) where T : PieceHolder<PlacedPiece>, T : PieceEventCaller = traceMove(holder, holder, *moves)

}