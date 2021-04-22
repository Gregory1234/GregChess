package gregc.gregchess.chess

import gregc.gregchess.buildTextComponent
import gregc.gregchess.chess.ChessVariant.MoveLegality.*
import gregc.gregchess.chess.component.Chessboard
import gregc.gregchess.glog
import org.bukkit.Material
import org.bukkit.Sound
import java.util.*

class ChessPiece(
    val type: ChessType,
    val side: ChessSide,
    initSquare: ChessSquare,
    hasMoved: Boolean = false
) {
    val standardName
        get() = "${side.standardName} ${type.name.toLowerCase()}"

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
        "ChessPiece(uniqueId = $uniqueId, pos = $pos, type = $type, side = $side, hasMoved = $hasMoved)"

    private val game
        get() = square.game

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
            board.renderer.renderPiece(loc, type.getStructure(side))
        }

        fun hide() {
            board.renderer.renderPiece(loc, type.getStructure(side).map { Material.AIR })
        }
    }

    private val loc
        get() = board.renderer.getPieceLoc(pos)

    init {
        render()
        glog.low("Piece created", game.uniqueId, uniqueId, pos, type, side)
    }

    private fun render() {
        board.renderer.renderPiece(loc, type.getStructure(side))
    }

    private fun hide() {
        board.renderer.renderPiece(loc, type.getStructure(side).map { Material.AIR })
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

    fun capture(by: ChessSide): Captured {
        glog.mid("Captured", type, "at", pos)
        clear()
        val captured = Captured(type, side, by, square.board)
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
        board.renderer.playPieceSound(pos, s)
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

    fun getInfo() = buildTextComponent {
        append("Name: $standardName\n")
        appendCopy("UUID: $uniqueId\n", uniqueId)
        append("Position: $pos\n")
        append(if (hasMoved) "Has moved\n" else "Has not moved\n")
        appendCopy("Game: ${game.uniqueId}\n", game.uniqueId)
        val moves = square.bakedMoves.orEmpty()
        append("All moves: ${moves.joinToString { it.baseStandardName() }}")
        val reasons =
            mapOf(
                LEGAL to "Legal moves",
                PINNED to "Moves blocked by pins",
                IN_CHECK to "Moves blocked because of checks",
                INVALID to "Invalid moves",
                SPECIAL to "Moves blocked for other reasons"
            )
        moves.groupBy { m -> reasons[game.variant.getLegality(m)] }.forEach { (l, m) ->
            if (l != null) {
                append("\n")
                append("$l: ${m.joinToString { it.baseStandardName() }}")
            }
        }
    }
}