package gregc.gregchess.chess.move

import gregc.gregchess.chess.*
import gregc.gregchess.chess.piece.*

interface ChessboardView : PieceHolderView<BoardPiece> {
    operator fun get(pos: Pos): BoardPiece?
    fun getFlags(pos: Pos): Map<ChessFlag, Int>
    fun hasActiveFlag(pos: Pos, flag: ChessFlag): Boolean = getFlags(pos)[flag]?.let(flag.isActive) ?: false
    fun kingOf(color: Color) = piecesOf(color).firstOrNull { it.type == PieceType.KING }
    fun getMoves(pos: Pos): List<Move>
    fun getLegalMoves(pos: Pos): List<Move>
    operator fun <T : Any> get(option: ChessVariantOption<T>): T
    val chess960: Boolean
    override fun canExist(p: BoardPiece): Boolean = get(p.pos) == null
    override fun exists(p: BoardPiece): Boolean = get(p.pos) == p
}