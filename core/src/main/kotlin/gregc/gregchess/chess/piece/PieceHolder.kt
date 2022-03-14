package gregc.gregchess.chess.piece

import gregc.gregchess.chess.*
import gregc.gregchess.chess.move.ChessboardView
import gregc.gregchess.chess.move.MoveEnvironment

interface PieceHolderView<P : PlacedPiece> {
    val pieces: Collection<P>
    fun piecesOf(color: Color) = pieces.filter { it.color == color }
    fun piecesOf(color: Color, type: PieceType) = pieces.filter { it.color == color && it.type == type }
    fun exists(p: P): Boolean
    fun canExist(p: P): Boolean
    fun checkExists(p: P) {
        if (!exists(p))
            throw PieceDoesNotExistException(p)
    }
    fun checkCanExist(p: P) {
        if (!canExist(p))
            throw PieceAlreadyOccupiesSquareException(p)
    }
}

interface PieceHolder<P : PlacedPiece> : PieceHolderView<P> {
    fun create(p: P)
    fun destroy(p: P)
}

fun interface PieceEventCaller {
    fun callPieceMoveEvent(e: PieceMoveEvent)
}

operator fun PieceEventCaller.invoke(vararg moves: Pair<PlacedPiece?, PlacedPiece?>?) = callPieceMoveEvent(PieceMoveEvent(listOfNotNull(*moves)))

class AddPieceHoldersEvent internal constructor(private val holders: MutableMap<PlacedPieceType<*, *>, PieceHolder<*>>) : ChessEvent {
    operator fun <P : PlacedPiece, H : PieceHolder<P>> set(type: PlacedPieceType<P, H>, holder: H) = holders.put(type, holder)
}

fun <P : PlacedPiece, T> T.sendSpawned(p: P) where T : PieceHolder<P>, T : PieceEventCaller = sendSpawned(p, this)
fun <P : PlacedPiece, T> T.spawn(p: P) where T : PieceHolder<P>, T : PieceEventCaller = spawn(p, this)
fun <P : PlacedPiece, T> T.clear(p: P) where T : PieceHolder<P>, T : PieceEventCaller = clear(p, this)
fun <P : PlacedPiece, T> T.multiMove(vararg moves: Pair<P?, P?>?) where T : PieceHolder<P>, T : PieceEventCaller = multiMove(this, *moves)

fun <P : PlacedPiece> PieceHolder<P>.sendSpawned(p: P, callEvent: PieceEventCaller) {
    checkExists(p)
    callEvent(null to p)
}
fun <P : PlacedPiece> PieceHolder<P>.spawn(p: P, callEvent: PieceEventCaller) {
    create(p)
    callEvent(null to p)
}
fun <P : PlacedPiece> PieceHolder<P>.clear(p: P, callEvent: PieceEventCaller) {
    destroy(p)
    callEvent(p to null)
}

fun <P : PlacedPiece> PieceHolder<P>.multiMove(callEvent: PieceEventCaller, vararg moves: Pair<P?, P?>?) {
    val destroyed = mutableListOf<P>()
    val created = mutableListOf<P>()
    val realMoves = moves.filterNotNull()
    try {
        for ((o, _) in realMoves)
            if (o != null)
                checkExists(o)
        for ((o, _) in realMoves)
            if (o != null) {
                destroy(o)
                destroyed += o
            }
        for ((_, t) in realMoves)
            if (t != null)
                checkCanExist(t)
        for ((_, t) in realMoves)
            if (t != null) {
                create(t)
                created += t
            }
        callEvent(*moves)
    } catch (e: Throwable) {
        for (t in created.asReversed())
            destroy(t)
        for (o in destroyed.asReversed())
            create(o)
        throw e
    }
}

interface BoardPieceHolder : PieceHolder<BoardPiece>, ChessboardView {
    fun addFlag(pos: Pos, flag: ChessFlag, age: UInt = 0u)
    override fun exists(p: BoardPiece) = super.exists(p)
    override fun canExist(p: BoardPiece) = super.canExist(p)
}

val MoveEnvironment.boardView: BoardPieceHolder get() = get(PlacedPieceType.BOARD)