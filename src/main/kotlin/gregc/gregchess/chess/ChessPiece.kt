package gregc.gregchess.chess

import gregc.gregchess.buildTextComponent
import gregc.gregchess.chess.variant.ChessVariant.MoveLegality.*
import gregc.gregchess.glog
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.inventory.ItemStack
import java.util.*

class ChessPiece(
    val type: ChessType,
    val side: ChessSide,
    initSquare: ChessSquare,
    hasMoved: Boolean = false
) {
    val standardName
        get() = "${side.standardName} ${type.name.lowercase()}"

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

    val item: ItemStack
        get() = type.getItem(side)

    class Captured(
        val type: ChessType,
        val side: ChessSide,
        val by: ChessSide,
        val pos: Pair<Int, Int>,
        private val game: ChessGame
    ) {
        private val loc
            get() = game.renderer.getCapturedLoc(pos, by)

        fun render() {
            game.renderer.renderPiece(loc, type.getStructure(side))
        }

        fun hide() {
            game.renderer.renderPiece(loc, type.getStructure(side).map { Material.AIR })
        }
    }

    private val loc
        get() = game.renderer.getPieceLoc(pos)

    init {
        render()
        glog.low("Piece created", game.uniqueId, uniqueId, pos, type, side)
    }

    private fun render() {
        game.renderer.renderPiece(loc, type.getStructure(side))
    }

    private fun hide() {
        game.renderer.renderPiece(loc, type.getStructure(side).map { Material.AIR })
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
        val captured = Captured(type, side, by, board.nextCapturedPos(type, by), game)
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
        game.renderer.playPieceSound(pos, s)
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