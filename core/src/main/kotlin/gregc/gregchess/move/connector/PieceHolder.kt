package gregc.gregchess.move.connector

import gregc.gregchess.Color
import gregc.gregchess.event.*
import gregc.gregchess.match.*
import gregc.gregchess.piece.*

interface PieceHolderView<P : PlacedPiece> {
    val pieces: List<P>
    fun piecesOf(color: Color): List<P> = pieces.filter { it.color == color }
    fun piecesOf(color: Color, type: PieceType): List<P> = pieces.filter { it.color == color && it.type == type }
    fun pieces(piece: Piece): List<P> = pieces.filter { it.piece == piece }
    fun canExist(p: P): Boolean
    fun exists(p: P): Boolean
}

interface PieceHolder<P : PlacedPiece> : PieceHolderView<P> {
    fun create(p: P)
    fun destroy(p: P)
}

class PieceMoveEvent(val moves: List<Pair<PlacedPiece?, PlacedPiece?>>) : ChessEvent {
    constructor(vararg moves: Pair<PlacedPiece?, PlacedPiece?>?) : this(moves.filterNotNull())

    override val type get() = ChessEventType.PIECE_MOVE
}

class PieceDoesNotExistException(val piece: PlacedPiece) : IllegalStateException(piece.toString())
class PieceCannotExistException(val piece: PlacedPiece) : IllegalStateException(piece.toString())

fun <P : PlacedPiece> PieceHolder<P>.checkExists(p: P) {
    if (!exists(p))
        throw PieceDoesNotExistException(p)
}

fun <P : PlacedPiece> PieceHolder<P>.checkCanExist(p: P) {
    if (!canExist(p))
        throw PieceCannotExistException(p)
}

fun <P : PlacedPiece> PieceHolder<P>.createSpawnedEvent(p: P): PieceMoveEvent {
    checkExists(p)
    return PieceMoveEvent(null to p)
}
fun <P : PlacedPiece> PieceHolder<P>.createSpawnEvent(p: P): PieceMoveEvent {
    create(p)
    return PieceMoveEvent(null to p)
}
fun <P : PlacedPiece> PieceHolder<P>.createClearEvent(p: P): PieceMoveEvent {
    destroy(p)
    return PieceMoveEvent(p to null)
}

fun <P : PlacedPiece, T> T.callSpawnedEvent(p: P) where T : PieceHolder<P>, T : ChessEventCaller = callEvent(createSpawnedEvent(p))
fun <P : PlacedPiece, T> T.callSpawnEvent(p: P) where T : PieceHolder<P>, T : ChessEventCaller = callEvent(createSpawnEvent(p))
fun <P : PlacedPiece, T> T.callClearEvent(p: P) where T : PieceHolder<P>, T : ChessEventCaller = callEvent(createClearEvent(p))

fun createMultiMoveEvent(holders: Map<PlacedPieceType<*>, PieceHolder<*>>, vararg moves: Pair<PlacedPiece?, PlacedPiece?>?): PieceMoveEvent {
    @Suppress("UNCHECKED_CAST")
    fun PlacedPiece.withHolder(f: PieceHolder<PlacedPiece>.(PlacedPiece) -> Unit) = (holders[placedPieceType]!! as PieceHolder<PlacedPiece>).f(this)
    val destroyed = mutableListOf<PlacedPiece>()
    val created = mutableListOf<PlacedPiece>()
    val realMoves = moves.filterNotNull()
    try {
        for ((o, _) in realMoves)
            o?.withHolder(PieceHolder<PlacedPiece>::checkExists)
        for ((o, _) in realMoves)
            if (o != null) {
                o.withHolder(PieceHolder<PlacedPiece>::destroy)
                destroyed += o
            }
        for ((_, t) in realMoves)
            t?.withHolder(PieceHolder<PlacedPiece>::checkCanExist)
        for ((_, t) in realMoves)
            if (t != null) {
                t.withHolder(PieceHolder<PlacedPiece>::create)
                created += t
            }
        return PieceMoveEvent(*moves)
    } catch (e: Throwable) {
        for (t in created.asReversed())
            t.withHolder(PieceHolder<PlacedPiece>::destroy)
        for (o in destroyed.asReversed())
            o.withHolder(PieceHolder<PlacedPiece>::create)
        throw e
    }
}

fun MoveConnector.createMultiMoveEvent(vararg moves: Pair<PlacedPiece?, PlacedPiece?>?) = createMultiMoveEvent(holders, *moves)
fun <T> T.callMultiMoveEvent(vararg moves: Pair<PlacedPiece?, PlacedPiece?>?) where T : MoveConnector, T : ChessEventCaller = callEvent(createMultiMoveEvent(*moves))