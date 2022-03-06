package gregc.gregchess.chess.move

import gregc.gregchess.chess.*
import gregc.gregchess.chess.piece.*
import gregc.gregchess.chess.variant.ChessVariant

interface MoveEnvironment : PieceHolder<PlacedPiece>, ComponentHolder {
    fun updateMoves()
    fun addFlag(pos: Pos, flag: ChessFlag, age: UInt = 0u)
    fun callEvent(e: ChessEvent)

    override fun callPieceMoveEvent(vararg moves: Pair<PlacedPiece?, PlacedPiece?>?) = callEvent(PieceMoveEvent(listOfNotNull(*moves)))
    val variant: ChessVariant

    val boardView: ChessboardView

    fun <T : PlacedPiece> piecesOf(t: PlacedPieceType<T>): Collection<T>
    fun <T : PlacedPiece> piecesOf(t: PlacedPieceType<T>, color: Color) = piecesOf(t).filter { it.color == color }
    fun <T : PlacedPiece> piecesOf(t: PlacedPieceType<T>, color: Color, type: PieceType) = piecesOf(t).filter { it.color == color && it.type == type }
}