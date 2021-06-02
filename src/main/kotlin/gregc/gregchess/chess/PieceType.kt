package gregc.gregchess.chess

import gregc.gregchess.Config
import gregc.gregchess.chatColor
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

    val view get() = Config.chess.piece[this]

    companion object {
        fun parseFromChar(c: Char) =
            values().firstOrNull { it.char == c.lowercaseChar() }
                ?: throw IllegalArgumentException(c.toString())

        fun parseFromStandardChar(c: Char): PieceType =
            values().firstOrNull { it.standardChar == c.lowercaseChar() }
                ?: throw IllegalArgumentException(c.toString())
    }

    fun getItem(side: Side): ItemStack {
        val item = ItemStack(view.item[side])
        val meta = item.itemMeta!!
        meta.setDisplayName(chatColor(side.getPieceName(pieceName)))
        item.itemMeta = meta
        return item
    }

    val pieceName
        get() = view.name
    val char
        get() = view.char
}