package gregc.gregchess.chess

import gregc.gregchess.Loc
import gregc.gregchess.getBlockAt
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block

data class ChessPiece(val type: ChessType, val side: ChessSide, val pos: ChessPosition, val hasMoved: Boolean) {

    data class Captured(val type: ChessType, val side: ChessSide, val by: ChessSide) {
        private val material = type.getMaterial(side)

        fun render(block: Block) {
            block.type = material
        }

        fun hide(block: Block) {
            block.type = Material.AIR
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

    fun toCaptured() = Captured(type, side, !side)

    val promotions =
        if (type == ChessType.PAWN) listOf(ChessType.QUEEN, ChessType.ROOK, ChessType.BISHOP, ChessType.KNIGHT)
        else emptyList()
}