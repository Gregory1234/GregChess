package gregc.gregchess.move.connector

import gregc.gregchess.*
import gregc.gregchess.event.ChessEventCaller
import gregc.gregchess.move.Move
import gregc.gregchess.piece.*

interface ChessboardView : PieceHolderView<BoardPiece> {
    operator fun get(pos: Pos): BoardPiece?
    fun kingOf(color: Color): BoardPiece? = piecesOf(color, PieceType.KING).firstOrNull()

    val captured: PieceHolderView<CapturedPiece>
    val halfmoveClock: Int
    fun getMoves(pos: Pos): List<Move>
    fun getLegalMoves(pos: Pos): List<Move>

    fun getFlags(pos: Pos): Map<ChessFlag, Int>
    operator fun get(pos: Pos, flag: ChessFlag): Int?
    fun hasActiveFlag(pos: Pos, flag: ChessFlag) = get(pos, flag)?.let(flag.isActive) ?: false

    override fun exists(p: BoardPiece): Boolean = get(p.pos) == p
    override fun canExist(p: BoardPiece): Boolean = get(p.pos) == null
}

interface ChessboardConnector : PieceHolder<BoardPiece>, ChessboardView, MoveConnector {
    override val captured: PieceHolder<CapturedPiece>
    override var halfmoveClock: Int

    operator fun set(pos: Pos, flag: ChessFlag, age: Int)
    override val holders: Map<PlacedPieceType<*>, PieceHolder<*>>
        get() = mapOf(PlacedPieceType.BOARD to this, PlacedPieceType.CAPTURED to captured)

    override fun exists(p: BoardPiece): Boolean = super.exists(p)
    override fun canExist(p: BoardPiece): Boolean = super.canExist(p)
}

interface ChessboardFacadeConnector : ChessboardConnector, ChessEventCaller {
    fun updateMoves()
}