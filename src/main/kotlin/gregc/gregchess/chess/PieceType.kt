package gregc.gregchess.chess

import gregc.gregchess.Config
import gregc.gregchess.chatColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.inventory.ItemStack
import kotlin.reflect.KProperty1

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

    private val view get() = Config.chess.piece[this]

    fun getMaterial(side: Side): Material = view.item[side]

    fun getStructure(side: Side): List<Material> = view.structure[side]

    companion object {
        fun parseFromChar(c: Char) =
            values().firstOrNull { it.char == c.lowercaseChar() }
                ?: throw IllegalArgumentException(c.toString())

        fun parseFromStandardChar(c: Char): PieceType =
            values().firstOrNull { it.standardChar == c.lowercaseChar() }
                ?: throw IllegalArgumentException(c.toString())
    }

    fun getItem(side: Side): ItemStack {
        val item = ItemStack(getMaterial(side))
        val meta = item.itemMeta!!
        meta.setDisplayName(chatColor(side.getPieceName(pieceName)))
        item.itemMeta = meta
        return item
    }

    fun getSound(name: KProperty1<Config.Chess.Piece.PieceData.Sound, Sound>): Sound = name.get(view.sound)

    val pieceName
        get() = view.name
    val char
        get() = view.char
}