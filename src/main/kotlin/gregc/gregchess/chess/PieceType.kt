package gregc.gregchess.chess

import gregc.gregchess.Configurator
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

        fun parseFromStandardChar(c: Char): PieceType =
            values().firstOrNull { it.standardChar == c.lowercaseChar() }
                ?: throw IllegalArgumentException(c.toString())
    }

    fun getItem(config: Configurator, side: Side): ItemStack {
        val item = ItemStack(view.item[side].get(config))
        val meta = item.itemMeta!!
        meta.setDisplayName(chatColor(side.getPieceName(config, pieceName.get(config))))
        item.itemMeta = meta
        return item
    }

    val pieceName
        get() = view.name
    val char
        get() = view.char
}