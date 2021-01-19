package gregc.gregchess.chess

import gregc.gregchess.Loc
import gregc.gregchess.chatColor
import gregc.gregchess.chess.component.Chessboard
import gregc.gregchess.getBlockAt
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.inventory.ItemStack
import java.lang.IllegalArgumentException

data class ChessPiece(val type: Type, val side: ChessSide, val pos: ChessPosition, val hasMoved: Boolean) {

    enum class Type(
        private val prettyName: String,
        val character: Char,
        private val matWhite: Material,
        private val matBlack: Material,
        val pickUpSound: Sound,
        val moveSound: Sound,
        val captureSound: Sound,
        val moveScheme: (ChessPosition, Chessboard) -> List<ChessMove>,
        val minor: Boolean
    ) {
        KING(
            "King",
            'k',
            Material.WHITE_CONCRETE,
            Material.BLACK_CONCRETE,
            Sound.BLOCK_METAL_HIT,
            Sound.BLOCK_METAL_STEP,
            Sound.ENTITY_ENDER_DRAGON_DEATH,
            ::kingMovement,
            false
        ),
        QUEEN(
            "Queen",
            'q',
            Material.DIAMOND_BLOCK,
            Material.NETHERITE_BLOCK,
            Sound.ENTITY_WITCH_CELEBRATE,
            Sound.BLOCK_GLASS_STEP,
            Sound.ENTITY_WITCH_DEATH,
            ::queenMovement,
            false
        ),
        ROOK(
            "Rook",
            'r',
            Material.IRON_BLOCK,
            Material.GOLD_BLOCK,
            Sound.ENTITY_IRON_GOLEM_STEP,
            Sound.ENTITY_IRON_GOLEM_STEP,
            Sound.ENTITY_IRON_GOLEM_DEATH,
            ::rookMovement,
            false
        ),
        BISHOP(
            "Bishop",
            'b',
            Material.POLISHED_DIORITE,
            Material.POLISHED_BLACKSTONE,
            Sound.ENTITY_SPIDER_AMBIENT,
            Sound.ENTITY_SPIDER_STEP,
            Sound.ENTITY_SPIDER_DEATH,
            ::bishopMovement,
            true
        ),
        KNIGHT(
            "Knight",
            'n',
            Material.END_STONE,
            Material.BLACKSTONE,
            Sound.ENTITY_HORSE_JUMP,
            Sound.ENTITY_HORSE_STEP,
            Sound.ENTITY_HORSE_DEATH,
            ::knightMovement,
            true
        ),
        PAWN(
            "Pawn",
            'p',
            Material.WHITE_CARPET,
            Material.BLACK_CARPET,
            Sound.BLOCK_STONE_HIT,
            Sound.BLOCK_STONE_STEP,
            Sound.BLOCK_STONE_BREAK,
            ::pawnMovement,
            false
        );

        fun getMaterial(side: ChessSide) = when (side) {
            ChessSide.WHITE -> matWhite
            ChessSide.BLACK -> matBlack
        }

        companion object {
            fun parseFromChar(c: Char) = when (c.toLowerCase()) {
                'k' -> KING
                'q' -> QUEEN
                'r' -> ROOK
                'b' -> BISHOP
                'n' -> KNIGHT
                'p' -> PAWN
                else -> throw IllegalArgumentException(c.toString())
            }
        }

        fun getItem(side: ChessSide): ItemStack {
            val item = ItemStack(getMaterial(side))
            val meta = item.itemMeta!!
            meta.setDisplayName(chatColor("&b${side.prettyName} $prettyName"))
            item.itemMeta = meta
            return item
        }


    }

    data class Captured(val type: Type, val side: ChessSide, val by: ChessSide, val pos: Pair<Int, Int>) {
        private val material = type.getMaterial(side)

        private val loc: Loc = when(by){
            ChessSide.WHITE -> Loc(4*8-1-2*pos.first, 101, 8-3-2*pos.second)
            ChessSide.BLACK -> Loc(8+2*pos.first, 101, 8*4+2+2*pos.second)
        }

        fun render(world: World) {
            world.getBlockAt(loc).type = material
        }

        fun hide(world: World) {
            world.getBlockAt(loc).type = Material.AIR
        }
    }

    override fun toString() = "ChessPiece(type = $type, side = $side, pos = $pos, hasMoved = $hasMoved)"
    val material = type.getMaterial(side)

    fun render(block: Block) {
        block.type = material
    }

    fun hide(block: Block) {
        block.type = Material.AIR
    }

    fun toCaptured(pos: Pair<Int, Int>) = Captured(type, side, !side, pos)

    val promotions = if (type == Type.PAWN) listOf(Type.QUEEN, Type.ROOK, Type.BISHOP, Type.KNIGHT) else emptyList()
}