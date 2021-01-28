package gregc.gregchess.chess

import gregc.gregchess.getBlockAt
import gregc.gregchess.playSound
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.block.Block

class ChessPiece(val type: ChessType, val side: ChessSide, initSquare: ChessSquare, hasMoved: Boolean = false) {
    var square: ChessSquare = initSquare
        private set(v) {
            hide()
            field = v
            render()
        }
    val pos
        get() = square.pos
    var hasMoved = hasMoved
        private set

    private val board
        get() = square.board

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

    fun move(target: ChessSquare) {
        target.piece = this
        square.piece = null
        square = target
        hasMoved = true
        playMoveSound()
    }

    fun swap(target: ChessPiece) {
        val tmp = target.square
        target.square = square
        target.hasMoved = true
        square = tmp
        hasMoved = true
        playMoveSound()
        target.playMoveSound()
    }

    fun pickUp() {
        hide()
        playPickUpSound()
    }

    fun placeDown() {
        render()
        playMoveSound()
    }

    fun capture() {
        hide()
        square.piece = null
        board += Captured(type, side, !side)
        playCaptureSound()
    }

    fun promote(promotion: ChessType) {
        hide()
        square.piece = ChessPiece(promotion, side, square)
    }

    private fun playSound(s: Sound) {
        loc.toLocation(board.game.world).playSound(s)
    }

    fun playPickUpSound() = playSound(type.pickUpSound)
    fun playMoveSound() = playSound(type.moveSound)
    fun playCaptureSound() = playSound(type.captureSound)

    fun force(hasMoved: Boolean) {
        this.hasMoved = hasMoved
    }
}