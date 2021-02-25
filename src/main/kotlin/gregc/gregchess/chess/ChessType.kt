package gregc.gregchess.chess

import gregc.gregchess.ConfigManager
import gregc.gregchess.chatColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.inventory.ItemStack
import java.lang.IllegalArgumentException

enum class ChessType(
    val path: String,
    val moveScheme: (ChessPiece) -> List<ChessMove>,
    val minor: Boolean
) {
    KING("Chess.Piece.King", ::kingMovement, false),
    QUEEN("Chess.Piece.Queen", ::queenMovement, false),
    ROOK("Chess.Piece.Rook", ::rookMovement, false),
    BISHOP("Chess.Piece.Bishop", ::bishopMovement, true),
    KNIGHT("Chess.Piece.Knight", ::knightMovement, true),
    PAWN("Chess.Piece.Pawn", ::pawnMovement, false);

    fun getMaterial(config: ConfigManager, side: ChessSide): Material =
        config.getEnum("$path.Material.${side.prettyName}", Material.AIR, Material::class)

    companion object {
        fun parseFromChar(config: ConfigManager, c: Char) =
            values().firstOrNull { it.getChar(config) == c.toLowerCase() }
                ?: throw IllegalArgumentException(c.toString())
    }

    fun getItem(config: ConfigManager, side: ChessSide): ItemStack {
        val item = ItemStack(getMaterial(config, side))
        val meta = item.itemMeta!!
        meta.setDisplayName(chatColor("&b${side.prettyName} ${getName(config)}"))
        item.itemMeta = meta
        return item
    }

    fun getSound(config: ConfigManager, name: String): Sound =
        config.getEnum("$path.Sound.$name", Sound.BLOCK_STONE_HIT, Sound::class)

    fun getName(config: ConfigManager) = config.getString("$path.Name")
    fun getChar(config: ConfigManager) = config.getChar("$path.Char")

    val promotions
        get() = if (this == PAWN) listOf(QUEEN, ROOK, BISHOP, KNIGHT)
        else emptyList()
}