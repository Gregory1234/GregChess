package gregc.gregchess.chess.move

import gregc.gregchess.chess.*
import gregc.gregchess.chess.piece.BoardPiece
import gregc.gregchess.chess.piece.PieceType
import gregc.gregchess.chess.variant.ChessVariant

interface ChessboardView {
    operator fun get(pos: Pos): BoardPiece?
    fun getFlags(pos: Pos): Map<ChessFlag, UInt>
    fun hasActiveFlag(pos: Pos, flag: ChessFlag): Boolean = getFlags(pos)[flag]?.let(flag.isActive) ?: false
    fun getFEN(): FEN
    val pieces: Collection<BoardPiece>
    fun piecesOf(color: Color) = pieces.filter { it.color == color }
    fun piecesOf(color: Color, type: PieceType) = pieces.filter { it.color == color && it.type == type }
    fun kingOf(color: Color) = piecesOf(color).firstOrNull { it.type == PieceType.KING }
    fun getMoves(pos: Pos): List<Move>
    fun getLegalMoves(pos: Pos): List<Move>
    operator fun <T : Any> get(option: ChessVariantOption<T>): T
    val chess960: Boolean
    val variant: ChessVariant
}