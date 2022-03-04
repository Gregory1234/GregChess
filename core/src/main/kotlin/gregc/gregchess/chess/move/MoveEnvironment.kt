package gregc.gregchess.chess.move

import gregc.gregchess.chess.*
import gregc.gregchess.chess.piece.*

interface MoveEnvironment : PieceHolder<PlacedPiece>, ChessboardView, ComponentHolder {
    fun updateMoves()
    fun addFlag(pos: Pos, flag: ChessFlag, age: UInt = 0u)
    fun callEvent(e: ChessEvent)

    override fun callPieceMoveEvent(vararg moves: Pair<PlacedPiece?, PlacedPiece?>?) = callEvent(PieceMoveEvent(listOfNotNull(*moves)))
}