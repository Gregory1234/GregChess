package gregc.gregchess.chess

import gregc.gregchess.chess.component.Chessboard
import gregc.gregchess.getBlockAt
import org.bukkit.Material
import org.bukkit.block.Block

class ChessPiece(val type: ChessType, val side: ChessSide, initPos: ChessPosition, val board: Chessboard) {
    var pos = initPos
        set(v) {
            hide()
            field = v
            render()
        }
    var hasMoved = false

    data class Captured(val type: ChessType, val side: ChessSide, val by: ChessSide) {
        private val material = type.getMaterial(side)

        fun render(block: Block) {
            block.type = material
        }

        fun hide(block: Block) {
            block.type = Material.AIR
        }
    }
    private val loc
        get() = board.renderer.getPieceLoc(pos)

    private val block
        get() = board.game.world.getBlockAt(loc)

    init {
        render()
    }

    fun render() {
        block.type = type.getMaterial(side)
    }

    fun hide() {
        block.type = Material.AIR
    }



    fun toCaptured() = Captured(type, side, !side)
}