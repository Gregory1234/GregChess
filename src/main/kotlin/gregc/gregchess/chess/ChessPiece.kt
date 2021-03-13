package gregc.gregchess.chess

import gregc.gregchess.*
import gregc.gregchess.chess.component.Chessboard
import org.bukkit.Material
import org.bukkit.Sound

class ChessPiece(
    val type: ChessType,
    val side: ChessSide,
    initSquare: ChessSquare,
    hasMoved: Boolean = false
) {
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

    private val config
        get() = board.game.config

    class Captured(
        val type: ChessType,
        val side: ChessSide,
        val by: ChessSide,
        val board: Chessboard
    ) {

        private val config
            get() = board.game.config

        fun render(loc: Loc) {
            type.getStructure(config, side).forEachIndexed { i, m ->
                board.game.world.getBlockAt(loc.copy(y = loc.y + i)).type = m
            }
        }

        fun hide(loc: Loc) {
            type.getStructure(config, side).forEachIndexed { i, _ ->
                board.game.world.getBlockAt(loc.copy(y = loc.y + i)).type = Material.AIR
            }
        }
    }

    private val loc
        get() = board.renderer.getPieceLoc(pos)

    init {
        render()
    }

    private fun render() {
        type.getStructure(config, side).forEachIndexed { i, m ->
            board.game.world.getBlockAt(loc.copy(y = loc.y + i)).type = m
        }
    }

    private fun hide() {
        type.getStructure(config, side).forEachIndexed { i, _ ->
            board.game.world.getBlockAt(loc.copy(y = loc.y + i)).type = Material.AIR
        }
    }

    fun move(target: ChessSquare) {
        glog.mid("Moved", type, "from", pos, "to", target.pos)
        target.piece = this
        square.piece = null
        square = target
        hasMoved = true
        playMoveSound()
    }

    fun swap(target: ChessPiece) {
        glog.mid("Swapped", type, "at", pos, "with", target.type, "at", target.pos)
        target.square.piece = this
        hasMoved = true
        square.piece = target
        target.hasMoved = true
        val tmp = square
        square = target.square
        target.square = tmp
        render()
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

    fun capture(): Captured {
        glog.mid("Captured", type, "at", pos)
        clear()
        val captured = Captured(type, side, !side, square.board)
        board += captured
        playCaptureSound()
        return captured
    }

    fun promote(promotion: ChessType) {
        glog.mid("Promoted", type, "at", pos, "into", promotion)
        hide()
        square.piece = ChessPiece(promotion, side, square)
    }

    private fun playSound(s: Sound) {
        loc.toLocation(board.game.world).playSound(s)
    }

    private fun playPickUpSound() = playSound(type.getSound(config, "PickUp"))
    private fun playMoveSound() = playSound(type.getSound(config, "Move"))
    private fun playCaptureSound() = playSound(type.getSound(config, "Capture"))

    fun force(hasMoved: Boolean) {
        this.hasMoved = hasMoved
    }

    fun demote(piece: ChessPiece) {
        piece.render()
        hide()
        square.piece = piece
    }

    fun resurrect(captured: Captured) {
        board -= captured
        render()
        square.piece = this
        playMoveSound()
    }

    fun clear() {
        hide()
        square.piece = null
    }
}