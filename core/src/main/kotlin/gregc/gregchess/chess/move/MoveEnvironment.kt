package gregc.gregchess.chess.move

import gregc.gregchess.chess.*
import gregc.gregchess.chess.piece.*
import gregc.gregchess.chess.variant.ChessVariant

@Suppress("UNCHECKED_CAST")
interface MoveEnvironment : PieceHolder<PlacedPiece>, ComponentHolder, PieceEventCaller {
    fun updateMoves()
    fun addFlag(pos: Pos, flag: ChessFlag, age: UInt = 0u)
    fun callEvent(e: ChessEvent)

    override fun callPieceMoveEvent(e: PieceMoveEvent) = callEvent(e)
    val variant: ChessVariant

    val boardView: ChessboardView

    operator fun <P : PlacedPiece> get(p: PlacedPieceType<P>): PieceHolder<P>

    override fun checkExists(p: PlacedPiece) = (get(p.placedPieceType) as PieceHolder<PlacedPiece>).checkExists(p)
    override fun checkCanExist(p: PlacedPiece) = (get(p.placedPieceType) as PieceHolder<PlacedPiece>).checkCanExist(p)
    override fun create(p: PlacedPiece) = (get(p.placedPieceType) as PieceHolder<PlacedPiece>).create(p)
    override fun destroy(p: PlacedPiece) = (get(p.placedPieceType) as PieceHolder<PlacedPiece>).destroy(p)

    fun <T : PlacedPiece> piecesOf(t: PlacedPieceType<T>): Collection<T> = get(t).pieces
    fun <T : PlacedPiece> piecesOf(t: PlacedPieceType<T>, color: Color) = get(t).piecesOf(color)
    fun <T : PlacedPiece> piecesOf(t: PlacedPieceType<T>, color: Color, type: PieceType) = get(t).piecesOf(color, type)
}