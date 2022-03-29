package gregc.gregchess.board

import gregc.gregchess.chess.ChessFlag
import gregc.gregchess.chess.Pos
import gregc.gregchess.chess.piece.*
import gregc.gregchess.move.MoveEnvironment

interface BoardPieceHolder : PieceHolder<BoardPiece>, ChessboardView {
    fun addFlag(pos: Pos, flag: ChessFlag, age: Int = 0)
    override fun exists(p: BoardPiece) = super.exists(p)
    override fun canExist(p: BoardPiece) = super.canExist(p)
}

val MoveEnvironment.boardView: BoardPieceHolder get() = get(PlacedPieceType.BOARD)