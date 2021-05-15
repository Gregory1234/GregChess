package gregc.gregchess.chess

import gregc.gregchess.ConfigManager
import gregc.gregchess.chatColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.inventory.ItemStack

enum class ChessType(
    path: String,
    val standardChar: Char,
    val moveScheme: (ChessPiece) -> List<MoveCandidate>,
    val minor: Boolean
) {
    KING("Chess.Piece.King", 'k', ::kingMovement, false),
    QUEEN("Chess.Piece.Queen", 'q', ::queenMovement, false),
    ROOK("Chess.Piece.Rook", 'r', ::rookMovement, false),
    BISHOP("Chess.Piece.Bishop", 'b', ::bishopMovement, true),
    KNIGHT("Chess.Piece.Knight", 'n', ::knightMovement, true),
    PAWN("Chess.Piece.Pawn", 'p', ::pawnMovement, false);

    private val view = ConfigManager.getView(path)

    fun getMaterial(side: ChessSide): Material =
        view.getEnum("Item.${side.standardName}", Material.AIR, Material::class)

    fun getStructure(side: ChessSide): List<Material> =
        view.getEnumList("Structure.${side.standardName}", Material::class)

    companion object {
        fun parseFromChar(c: Char) =
            values().firstOrNull { it.char == c.lowercaseChar() }
                ?: throw IllegalArgumentException(c.toString())

        fun parseFromStandardChar(c: Char): ChessType =
            values().firstOrNull { it.standardChar == c.lowercaseChar() }
                ?: throw IllegalArgumentException(c.toString())
    }

    fun getItem(side: ChessSide): ItemStack {
        val item = ItemStack(getMaterial(side))
        val meta = item.itemMeta!!
        meta.setDisplayName(chatColor(side.getPieceName(pieceName)))
        item.itemMeta = meta
        return item
    }

    fun getSound(name: String): Sound =
        view.getEnum("Sound.$name", Sound.BLOCK_STONE_HIT, Sound::class)

    val pieceName
        get() = view.getString("Name")
    val char
        get() = view.getChar("Char")
}