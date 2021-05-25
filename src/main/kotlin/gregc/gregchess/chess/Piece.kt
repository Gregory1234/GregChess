package gregc.gregchess.chess

import gregc.gregchess.buildTextComponent
import gregc.gregchess.chess.variant.ChessVariant.MoveLegality.*
import gregc.gregchess.glog
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.inventory.ItemStack
import java.util.*

class Piece(val type: PieceType, val side: Side, initSquare: Square, hasMoved: Boolean = false) {
    val standardName
        get() = "${side.standardName} ${type.name.lowercase()}"

    var square: Square = initSquare
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
        "Piece(uniqueId = $uniqueId, pos = $pos, type = $type, side = $side, hasMoved = $hasMoved)"

    private val game
        get() = square.game

    private val board
        get() = square.board

    val item: ItemStack
        get() = type.getItem(side)

    data class Info(val pos: Pos, val type: PieceType, val side: Side, val hasMoved: Boolean) {
        val standardChar
            get() = when (side) {
                Side.WHITE -> type.standardChar.uppercaseChar()
                Side.BLACK -> type.standardChar
            }
    }

    val info
        get() = Info(pos, type, side, hasMoved)

    class Captured(
        val type: PieceType,
        val side: Side,
        val by: Side,
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

    fun move(target: Square) {
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

    fun capture(by: Side): Captured {
        glog.mid("Captured", type, "at", pos)
        clear()
        val captured = Captured(type, side, by, board.nextCapturedPos(type, by), game)
        board += captured
        playCaptureSound()
        return captured
    }

    fun promote(promotion: PieceType) {
        glog.mid("Promoted", type, "at", pos, "into", promotion)
        hide()
        square.piece = Piece(promotion, side, square)
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

    fun demote(piece: Piece) {
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
        fun autoMove(moves: Map<Piece, Square>) {
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
        moves.groupBy { m -> game.variant.getLegality(m) }.forEach { (l, m) ->
            append("\n${l.prettyName}: ${m.joinToString { it.baseStandardName() }}")
        }
    }
}