package gregc.gregchess.move

import gregc.gregchess.Color
import gregc.gregchess.match.ChessEvent
import gregc.gregchess.match.ComponentHolder
import gregc.gregchess.piece.*
import gregc.gregchess.variant.ChessVariant

@Suppress("UNCHECKED_CAST")
interface MoveEnvironment : PieceHolder<PlacedPiece>, ComponentHolder, PieceEventCaller {
    fun updateMoves()
    fun callEvent(e: ChessEvent)

    override fun callPieceMoveEvent(e: PieceMoveEvent) = callEvent(e)
    val variant: ChessVariant

    operator fun <P : PlacedPiece, H : PieceHolder<P>> get(p: PlacedPieceType<P, H>): H

    override fun exists(p: PlacedPiece) = (get(p.placedPieceType) as PieceHolder<PlacedPiece>).exists(p)
    override fun canExist(p: PlacedPiece) = (get(p.placedPieceType) as PieceHolder<PlacedPiece>).canExist(p)
    override fun checkExists(p: PlacedPiece) = (get(p.placedPieceType) as PieceHolder<PlacedPiece>).checkExists(p)
    override fun checkCanExist(p: PlacedPiece) = (get(p.placedPieceType) as PieceHolder<PlacedPiece>).checkCanExist(p)
    override fun create(p: PlacedPiece) = (get(p.placedPieceType) as PieceHolder<PlacedPiece>).create(p)
    override fun destroy(p: PlacedPiece) = (get(p.placedPieceType) as PieceHolder<PlacedPiece>).destroy(p)

    fun <P : PlacedPiece> piecesOf(t: PlacedPieceType<P, *>): Collection<P> = get(t).pieces
    fun <P : PlacedPiece> piecesOf(t: PlacedPieceType<P, *>, color: Color) = get(t).piecesOf(color)
    fun <P : PlacedPiece> piecesOf(t: PlacedPieceType<P, *>, color: Color, type: PieceType) = get(t).piecesOf(color, type)
}