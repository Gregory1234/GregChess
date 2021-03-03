package gregc.gregchess.chess

import gregc.gregchess.ConfigManager
import gregc.gregchess.Loc
import gregc.gregchess.chess.component.Chessboard
import gregc.gregchess.getBlockAt
import gregc.gregchess.playSound
import org.bukkit.Material
import org.bukkit.Sound

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

    private val config
        get() = board.game.config

    class Captured(val type: ChessType, val side: ChessSide, val by: ChessSide, val board: Chessboard) {

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

    fun capture(): Captured {
        hide()
        square.piece = null
        val captured = Captured(type, side, !side, square.board)
        board += captured
        playCaptureSound()
        return captured
    }

    fun promote(promotion: ChessType) {
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

    fun resurrect(captured: Captured){
        board -= captured
        render()
        square.piece = this
        playMoveSound()
    }
}