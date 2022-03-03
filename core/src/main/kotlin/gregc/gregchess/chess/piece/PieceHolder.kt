package gregc.gregchess.chess.piece

import gregc.gregchess.chess.ChessEvent

interface PieceHolder<in P : PlacedPiece> {
    fun checkExists(p: P)
    fun checkCanExist(p: P)
    fun create(p: P)
    fun destroy(p: P)
    fun callPieceMoveEvent(vararg moves: Pair<P?, P?>?)
    fun sendSpawned(p: P) {
        checkExists(p)
        callPieceMoveEvent(null to p)
    }
    fun spawn(p: P) {
        create(p)
        callPieceMoveEvent(null to p)
    }
    fun clear(p: P) {
        destroy(p)
        callPieceMoveEvent(p to null)
    }
}

class AddPieceHoldersEvent internal constructor(private val holders: MutableMap<PlacedPieceType<*>, PieceHolder<*>>) : ChessEvent {
    operator fun <T : PlacedPiece> set(type: PlacedPieceType<T>, holder: PieceHolder<T>) = holders.put(type, holder)
}

fun <P : PlacedPiece> multiMove(holder: PieceHolder<P>, vararg moves: Pair<P?, P?>?) {
    val destroyed = mutableListOf<P>()
    val created = mutableListOf<P>()
    val realMoves = moves.filterNotNull()
    try {
        for ((o, _) in realMoves)
            if (o != null)
                holder.checkExists(o)
        for ((o, _) in realMoves)
            if (o != null) {
                holder.destroy(o)
                destroyed += o
            }
        for ((_, t) in realMoves)
            if (t != null)
                holder.checkCanExist(t)
        for ((_, t) in realMoves)
            if (t != null) {
                holder.create(t)
                created += t
            }
        holder.callPieceMoveEvent(*moves)
    } catch (e: Throwable) {
        for (t in created.asReversed())
            holder.destroy(t)
        for (o in destroyed.asReversed())
            holder.create(o)
        throw e
    }
}