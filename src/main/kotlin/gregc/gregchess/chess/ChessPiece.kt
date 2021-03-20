package gregc.gregchess.chess

import gregc.gregchess.*
import gregc.gregchess.chess.component.Chessboard
import org.bukkit.Material
import org.bukkit.Sound
import java.util.*

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

    val uniqueId: UUID = UUID.randomUUID()

    override fun toString() =
        "ChessPiece(uniqueId = $uniqueId, pos = $pos, type = $type, side = $side)"

    private val board
        get() = square.board

    class Captured(
        val type: ChessType,
        val side: ChessSide,
        val by: ChessSide,
        private val board: Chessboard
    ) {

        private val loc
            get() = board.renderer.getCapturedLoc(this)

        fun render() {
            type.getStructure(side).forEachIndexed { i, m ->
                board.game.world.getBlockAt(loc.copy(y = loc.y + i)).type = m
            }
        }

        fun hide() {
            type.getStructure(side).forEachIndexed { i, _ ->
                board.game.world.getBlockAt(loc.copy(y = loc.y + i)).type = Material.AIR
            }
        }
    }

    private val loc
        get() = board.renderer.getPieceLoc(pos)

    init {
        render()
        glog.low("Piece created", board.game.uniqueId, uniqueId, pos, type, side)
    }

    private fun render() {
        type.getStructure(side).forEachIndexed { i, m ->
            board.game.world.getBlockAt(loc.copy(y = loc.y + i)).type = m
        }
    }

    private fun hide() {
        type.getStructure(side).forEachIndexed { i, _ ->
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

    private fun playPickUpSound() = playSound(type.getSound("PickUp"))
    private fun playMoveSound() = playSound(type.getSound("Move"))
    private fun playCaptureSound() = playSound(type.getSound("Capture"))

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

    companion object {
        fun autoMove(moves: Map<ChessPiece, ChessSquare>) {
            moves.forEach { (piece, target) ->
                glog.mid("Auto-moved", piece.type, "from", piece.pos, "to", target.pos)
                piece.hasMoved = true
                piece.square.piece = null
                piece.square = target
            }
            moves.forEach { (piece, target) ->
                target.piece = piece
                piece.render()
                piece.playMoveSound()
            }
        }
    }
}