package gregc.gregchess.chess

import gregc.gregchess.ConfigManager
import org.bukkit.Material
import kotlin.math.abs

class MoveData(
    val piece: ChessPiece,
    val origin: ChessSquare,
    val target: ChessSquare,
    val name: String,
    val standardName: String,
    val display: ChessSquare = target
) {

    fun undo() {
        TODO("Not yet implemented")
    }

    fun clear() {
        origin.previousMoveMarker = null
        origin.render()
        display.previousMoveMarker = null
        display.render()
    }

    fun render() {
        origin.previousMoveMarker = Material.BROWN_CONCRETE
        origin.render()
        display.previousMoveMarker = Material.ORANGE_CONCRETE
        display.render()
    }
}

abstract class MoveCandidate(
    val piece: ChessPiece, val target: ChessSquare, val floor: Material,
    val pass: Collection<ChessPosition>, val control: ChessSquare? = target,
    val promotion: ChessType? = null, val mustCapture: Boolean = false,
    val display: ChessSquare = target
) {

    companion object {
        val RED = Material.RED_CONCRETE
        val GREEN = Material.GREEN_CONCRETE
        val BLUE = Material.BLUE_CONCRETE
    }

    val origin = piece.square

    open fun execute(): MoveData {
        val base = baseName()
        val standardBase = baseStandardName()
        control?.piece?.capture(piece.side)
        piece.move(target)
        promotion?.let { piece.promote(it) }
        val ch = checkForChecks(piece.side, piece.square.game)
        return MoveData(piece, origin, target, base + ch, standardBase + ch, display)
    }

    open fun baseName() = buildString {
        if (piece.type != ChessType.PAWN) {
            append(piece.type.char.toUpperCase())
            append(getUniquenessCoordinate(piece, target))
        } else if (control != null)
            append(piece.pos.fileStr)
        if (control?.piece != null)
            append(ConfigManager.getString("Chess.Capture"))
        promotion?.let { append(it.char.toUpperCase()) }
        append(target.pos)
    }

    open fun baseStandardName() = buildString {
        if (piece.type != ChessType.PAWN) {
            append(piece.type.standardChar.toUpperCase())
            append(getUniquenessCoordinate(piece, target))
        } else if (control != null)
            append(origin.pos.fileStr)
        if (control?.piece != null)
            append("x")
        promotion?.let {
            append("=")
            append(it.standardChar.toUpperCase())
        }
        append(target.pos)
    }

    fun render() {
        display.moveMarker = floor
        display.render()
    }

    fun clear() {
        display.moveMarker = null
        display.render()
    }
}

fun getUniquenessCoordinate(piece: ChessPiece, target: ChessSquare): String {
    val game = target.game
    val pieces = game.board.pieces.filter { it.side == piece.side && it.type == piece.type }
    val consideredPieces =
        pieces.filter { p ->
            p.square.bakedMoves.orEmpty().any { it.target == target && game.variant.isLegal(it) }
        }
    return when {
        consideredPieces.size == 1 -> ""
        consideredPieces.count { it.pos.file == piece.pos.file } == 1 -> piece.pos.fileStr
        consideredPieces.count { it.pos.rank == piece.pos.rank } == 1 -> piece.pos.rankStr
        else -> piece.pos.toString()
    }
}

fun checkForChecks(side: ChessSide, game: ChessGame): String {
    game.board.updateMoves()
    val pieces = game.board.piecesOf(!side)
    val inCheck = game.variant.isInCheck(pieces.first { it.type == ChessType.KING })
    val noMoves = pieces.all { game.board.getMoves(it.pos).none(game.variant::isLegal) }
    return when {
        inCheck && noMoves -> "#"
        inCheck -> "+"
        else -> ""
    }
}

fun knightMovement(piece: ChessPiece): List<MoveCandidate> {
    return emptyList()
}

fun rookMovement(piece: ChessPiece): List<MoveCandidate> {
    return emptyList()
}

fun bishopMovement(piece: ChessPiece): List<MoveCandidate> {
    return emptyList()
}

fun queenMovement(piece: ChessPiece): List<MoveCandidate> {
    return emptyList()
}

fun kingMovement(piece: ChessPiece): List<MoveCandidate> {
    return emptyList()
}

fun pawnMovement(piece: ChessPiece): List<MoveCandidate> {

    fun ifProm(promotion: ChessType?, floor: Material) =
        if (promotion == null) floor else MoveCandidate.BLUE

    fun handlePromotion(pos: ChessPosition, f: (ChessType?) -> Unit) =
        if (pos.rank in listOf(0,7)) ChessType.PAWN.promotions.forEach(f) else f(null)

    class PawnPush(
        piece: ChessPiece, target: ChessSquare,
        pass: ChessPosition?, promotion: ChessType?
    ) : MoveCandidate(piece, target, ifProm(promotion, GREEN), listOfNotNull(pass), null, promotion)

    class PawnCapture(piece: ChessPiece, target: ChessSquare, promotion: ChessType?) :
        MoveCandidate(piece, target, ifProm(promotion, RED), emptyList(), target, promotion, true)

    class EnPassantCapture(piece: ChessPiece, target: ChessSquare, control: ChessSquare) :
        MoveCandidate(piece, target, RED, emptyList(), control, mustCapture = true) {
        override fun execute(): MoveData {
            val base = baseName()
            val standardBase = baseStandardName()
            control?.piece?.capture(piece.side)
            piece.move(target)
            val ch = checkForChecks(piece.side, piece.square.game)
            return MoveData(piece, origin, target, "$base$ch e.p.", standardBase + ch, display)
        }
    }

    val ret = mutableListOf<MoveCandidate>()
    piece.square.board.getSquare(piece.pos.plusR(piece.side.direction))?.let {t ->
        handlePromotion(t.pos) {
            ret += PawnPush(piece, t, null, it)
        }
    }
    if (!piece.hasMoved)
        piece.square.board.getSquare(piece.pos.plusR(2 * piece.side.direction))?.let { t ->
            handlePromotion(t.pos) {
                ret += PawnPush(piece, t, piece.pos.plusR(piece.side.direction), it)
            }
        }
    for (s in listOf(-1, 1)) {
        piece.square.board.getSquare(piece.pos + Pair(s, piece.side.direction))?.let { t ->
            handlePromotion(t.pos) {
                ret += PawnCapture(piece, t, it)
            }
        }
        val p = piece.square.board[piece.pos.plusF(s)]
        val lm = piece.square.board.lastMove
        if (p?.type == ChessType.PAWN
            && lm?.piece == p && abs(lm.origin.pos.rank - lm.target.pos.rank) == 2
        ) {
            piece.square.board.getSquare(piece.pos + Pair(s, piece.side.direction))?.let {
                ret += EnPassantCapture(piece, it, p.square)
            }
        }
    }
    return ret
}
