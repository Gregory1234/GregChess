package gregc.gregchess.chess

import gregc.gregchess.Config
import gregc.gregchess.snakeToPascal
import org.bukkit.inventory.ItemStack

enum class PieceType(
    val standardChar: Char,
    val moveScheme: (BoardPiece) -> List<MoveCandidate>,
    val minor: Boolean
) {
    KING('k', ::kingMovement, false),
    QUEEN('q', ::queenMovement, false),
    ROOK('r', ::rookMovement, false),
    BISHOP('b', ::bishopMovement, true),
    KNIGHT('n', ::knightMovement, true),
    PAWN('p', ::pawnMovement, false);

    companion object {

        fun parseFromStandardChar(c: Char): PieceType =
            values().firstOrNull { it.standardChar == c.lowercaseChar() }
                ?: throw IllegalArgumentException(c.toString())
    }

    fun getItem(side: Side): ItemStack {
        val item = ItemStack(itemMaterial[side])
        val meta = item.itemMeta!!
        meta.setDisplayName(side.getPieceName(pieceName))
        item.itemMeta = meta
        return item
    }

    fun getSound(s: PieceSound) = Config.piece.getPieceSound(this, s)

    val standardName: String = name.snakeToPascal()
    val pieceName get() = Config.piece.getPieceName(this)
    val char get() = Config.piece.getPieceChar(this)
    val itemMaterial get() = Config.piece.getPieceItem(this)
    val structure get() = Config.piece.getPieceStructure(this)
}