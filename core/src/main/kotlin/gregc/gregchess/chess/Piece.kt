package gregc.gregchess.chess

import gregc.gregchess.glog
import java.util.*

data class Piece(val type: PieceType, val side: Side) {
    val standardChar
        get() = when (side) {
            Side.WHITE -> type.standardChar.uppercaseChar()
            Side.BLACK -> type.standardChar
        }
}

fun PieceType.of(side: Side) = Piece(this, side)
val PieceType.white get() = of(Side.WHITE)
val PieceType.black get() = of(Side.BLACK)

data class CapturedPos(val by: Side, val pos: Pair<Int, Int>)

class CapturedPiece(val piece: Piece, val pos: CapturedPos) {
    val type = piece.type
    val side = piece.side

    override fun toString() = "CapturedPiece(type=$type, side=$side, pos=$pos)"
}

data class PieceInfo(val pos: Pos, val piece: Piece, val hasMoved: Boolean) {
    val type get() = piece.type
    val side get() = piece.side
    val standardChar get() = piece.standardChar
}

sealed class PieceEvent(val piece: BoardPiece) : ChessEvent {
    class Created(piece: BoardPiece) : PieceEvent(piece)
    class Cleared(piece: BoardPiece) : PieceEvent(piece)

    enum class ActionType {
        PICK_UP, PLACE_DOWN
    }

    class Action(piece: BoardPiece, val type: ActionType) : PieceEvent(piece)

    class Moved(piece: BoardPiece, val from: Pos) : PieceEvent(piece)
    class Captured(piece: BoardPiece, val captured: CapturedPiece) : PieceEvent(piece)
    class Promoted(piece: BoardPiece, val promotion: BoardPiece) : PieceEvent(piece)
    class Resurrected(piece: BoardPiece, val captured: CapturedPiece) : PieceEvent(piece)
    class MultiMoved(val moves: Map<BoardPiece, Pos>) : PieceEvent(moves.keys.first())
}

class PieceAlreadyOccupiesSquareException(val piece: Piece, val pos: Pos) : Exception("$pos, $piece")

class BoardPiece(val piece: Piece, initSquare: Square, hasMoved: Boolean = false, val uuid: UUID = UUID.randomUUID()) {
    val type = piece.type
    val side = piece.side

    var square: Square = initSquare
        private set
    val pos
        get() = square.pos
    var hasMoved = hasMoved
        private set

    override fun toString() = "Piece(uniqueId=$uuid, pos=$pos, type=$type, side=$side, hasMoved=$hasMoved)"

    private val game
        get() = square.game

    private val board
        get() = square.board

    val info
        get() = PieceInfo(pos, piece, hasMoved)

    init {
        game.components.callEvent(PieceEvent.Created(this))
        glog.low("Piece created", game.uuid, uuid, pos, type, side)
    }

    fun move(target: Square) {
        glog.mid("Moved", type, "from", pos, "to", target.pos)
        target.piece?.let {
            throw PieceAlreadyOccupiesSquareException(it.piece, target.pos)
        }
        target.piece = this
        square.piece = null
        val from = square.pos
        square = target
        hasMoved = true
        game.components.callEvent(PieceEvent.Moved(this, from))
    }

    fun pickUp() = game.components.callEvent(PieceEvent.Action(this, PieceEvent.ActionType.PICK_UP))

    fun placeDown() = game.components.callEvent(PieceEvent.Action(this, PieceEvent.ActionType.PLACE_DOWN))

    fun capture(by: Side): CapturedPiece {
        glog.mid("Captured", type, "at", pos)
        clear()
        val captured = CapturedPiece(piece, board.nextCapturedPos(type, by))
        board += captured
        game.components.callEvent(PieceEvent.Captured(this, captured))
        return captured
    }

    fun promote(promotion: Piece) {
        glog.mid("Promoted", type, "at", pos, "into", promotion)
        square.piece = BoardPiece(promotion, square)
        game.components.callEvent(PieceEvent.Promoted(this, square.piece!!))
    }

    fun force(hasMoved: Boolean) {
        this.hasMoved = hasMoved
    }

    fun demote(piece: BoardPiece) {
        game.components.callEvent(PieceEvent.Promoted(this, square.piece!!))
        square.piece = piece
    }

    fun resurrect(captured: CapturedPiece) {
        board -= captured
        square.piece = this
        game.components.callEvent(PieceEvent.Resurrected(this, captured))
    }

    fun clear() {
        game.components.callEvent(PieceEvent.Cleared(this))
        square.piece = null
    }

    companion object {
        fun autoMove(moves: Map<BoardPiece, Square>) {
            val org = moves.mapValues { (p, _) -> p.pos }
            moves.forEach { (piece, target) ->
                glog.mid("Auto-moved", piece.type, "from", piece.pos, "to", target.pos)
                piece.hasMoved = true
                target.piece?.let { p ->
                    if (moves.keys.none {it.square == target})
                        throw PieceAlreadyOccupiesSquareException(p.piece, target.pos)
                }
                piece.square.piece = null
                piece.square = target
            }
            moves.forEach { (piece, target) ->
                target.piece = piece
            }
            org.keys.firstOrNull()?.game?.components?.callEvent(PieceEvent.MultiMoved(org))
        }
    }
}