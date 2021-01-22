package gregc.gregchess.chess

import gregc.gregchess.Loc
import gregc.gregchess.getBlockAt
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block

data class ChessPiece(val type: ChessType, val side: ChessSide, val pos: ChessPosition, val hasMoved: Boolean) {

    data class Captured(val type: ChessType, val side: ChessSide, val by: ChessSide, val pos: Pair<Int, Int>) {
        private val material = type.getMaterial(side)

        private val loc: Loc = when (by) {
            ChessSide.WHITE -> Loc(4 * 8 - 1 - 2 * pos.first, 101, 8 - 3 - 2 * pos.second)
            ChessSide.BLACK -> Loc(8 + 2 * pos.first, 101, 8 * 4 + 2 + 2 * pos.second)
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

    val promotions =
        if (type == ChessType.PAWN) listOf(ChessType.QUEEN, ChessType.ROOK, ChessType.BISHOP, ChessType.KNIGHT)
        else emptyList()
}