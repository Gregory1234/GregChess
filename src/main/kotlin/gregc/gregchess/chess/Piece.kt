package gregc.gregchess.chess

import gregc.gregchess.buildTextComponent
import gregc.gregchess.glog
import java.util.*

data class Piece(val type: PieceType, val side: Side) {
    val standardName
        get() = "${side.standardName} ${type.name.lowercase()}"

    val standardChar
        get() = when (side) {
            Side.WHITE -> type.standardChar.uppercaseChar()
            Side.BLACK -> type.standardChar
        }
}

data class CapturedPos(val by: Side, val pos: Pair<Int, Int>)

class CapturedPiece(
    val piece: Piece,
    val pos: CapturedPos,
    private val game: ChessGame
) {
    val type = piece.type
    val side = piece.side

    fun render() {
        game.renderers.forEach { it.renderCapturedPiece(pos, piece) }
    }

    fun hide() {
        game.renderers.forEach { it.clearCapturedPiece(pos) }
    }
}

data class PieceInfo(val pos: Pos, val piece: Piece, val hasMoved: Boolean) {
    val type = piece.type
    val side = piece.side
    val standardChar
        get() = piece.standardChar
}

enum class PieceSound {
    PICK_UP, MOVE, CAPTURE
}

class BoardPiece(val piece: Piece, initSquare: Square, hasMoved: Boolean = false) {
    val standardName
        get() = piece.standardName
    val type = piece.type
    val side = piece.side

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

    val info
        get() = PieceInfo(pos, piece, hasMoved)

    init {
        render()
        glog.low("Piece created", game.uniqueId, uniqueId, pos, type, side)
    }

    private fun render() {
        game.renderers.forEach { it.renderPiece(pos, piece) }
    }

    private fun hide() {
        game.renderers.forEach { it.clearPiece(pos) }
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

    fun capture(by: Side): CapturedPiece {
        glog.mid("Captured", type, "at", pos)
        clear()
        val captured = CapturedPiece(piece, board.nextCapturedPos(type, by), game)
        board += captured
        playCaptureSound()
        return captured
    }

    fun promote(promotion: Piece) {
        glog.mid("Promoted", type, "at", pos, "into", promotion)
        hide()
        square.piece = BoardPiece(promotion, square)
    }

    private fun playSound(s: PieceSound) {
        game.renderers.forEach { it.playPieceSound(pos, s, type) }
    }

    private fun playPickUpSound() = playSound(PieceSound.PICK_UP)
    private fun playMoveSound() = playSound(PieceSound.MOVE)
    private fun playCaptureSound() = playSound(PieceSound.CAPTURE)

    fun force(hasMoved: Boolean) {
        this.hasMoved = hasMoved
    }

    fun demote(piece: BoardPiece) {
        piece.render()
        hide()
        square.piece = piece
    }

    fun resurrect(captured: CapturedPiece) {
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
        fun autoMove(moves: Map<BoardPiece, Square>) {
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