package gregc.gregchess.chess

import gregc.gregchess.*
import gregc.gregchess.chess.component.Chessboard
import org.bukkit.Material
import kotlin.math.*

data class MoveData(
    val piece: ChessPiece,
    val origin: ChessSquare,
    val target: ChessSquare,
    val name: String,
    inline val undo: () -> Unit
) {

    override fun toString() =
        "MoveData(name = $name, origin.pos = ${origin.pos}, target.pos = ${target.pos}, piece.uuid = ${piece.uniqueId})"

    fun render() {
        origin.previousMoveMarker = Material.BROWN_CONCRETE
        origin.render()
        target.previousMoveMarker = Material.ORANGE_CONCRETE
        target.render()
    }

    fun clear() {
        origin.previousMoveMarker = null
        origin.render()
        target.previousMoveMarker = null
        target.render()
    }
}

sealed class ChessMove(
    val piece: ChessPiece,
    val target: ChessSquare,
    val display: ChessSquare = target
) {
    val origin = piece.square

    fun render() {
        display.moveMarker = floor
        display.render()
    }

    fun clear() {
        display.moveMarker = null
        display.render()
    }

    abstract val isValid: Boolean

    abstract val floor: Material

    abstract val canAttack: Boolean

    abstract fun execute(config: ConfigManager): MoveData

    interface Promoting {
        val promotion: ChessType?
    }

    class Normal(
        piece: ChessPiece,
        target: ChessSquare,
        val defensive: Boolean = true,
        override val promotion: ChessType? = null
    ) : ChessMove(piece, target), Promoting {

        override val isValid: Boolean
            get() = true

        override val floor
            get() = if (promotion == null) Material.GREEN_CONCRETE else Material.BLUE_CONCRETE

        override val canAttack
            get() = defensive

        override fun execute(config: ConfigManager): MoveData {
            val pieceHasMoved = piece.hasMoved
            var name = ""
            if (piece.type != ChessType.PAWN)
                name += piece.type.getChar(config).toUpperCase()
            name += getUniquenessCoordinate(piece, target)
            name += target.pos.toString()
            piece.move(target)
            if (promotion != null) {
                piece.promote(promotion)
                name += promotion.getChar(config).toUpperCase()
            }
            name += checkForChecks(piece.side, piece.square.board)
            val undoReset = if (piece.type == ChessType.PAWN)
                piece.square.board.resetMovesSinceLastCapture()
            else
                piece.square.board::undoMove
            val undo = {
                if (promotion != null)
                    piece.square.piece?.demote(piece)
                piece.move(origin)
                undoReset()
                piece.force(pieceHasMoved)
            }
            return MoveData(piece, origin, target, name, undo)
        }
    }

    class Attack(
        piece: ChessPiece,
        target: ChessSquare,
        val capture: ChessSquare = target,
        val defensive: Boolean = false,
        val potentialBlocks: List<ChessPosition> = emptyList(),
        val actualBlocks: List<ChessPosition> = emptyList(),
        override val promotion: ChessType? = null
    ) : ChessMove(piece, target), Promoting {

        override val isValid: Boolean
            get() = actualBlocks.isEmpty() && !defensive

        override val canAttack
            get() = actualBlocks.isEmpty()

        override val floor
            get() = if (promotion == null) Material.RED_CONCRETE else Material.BLUE_CONCRETE

        override fun execute(config: ConfigManager): MoveData {
            val pieceHasMoved = piece.hasMoved
            var name = ""
            name += if (piece.type == ChessType.PAWN)
                piece.pos.fileStr
            else
                piece.type.getChar(config).toUpperCase()
            name += getUniquenessCoordinate(piece, target)
            name += config.getString("Chess.Capture")
            name += target.pos.toString()
            val capturedPiece = capture.piece
            val c = capturedPiece?.capture()
            piece.move(target)
            if (promotion != null) {
                piece.promote(promotion)
                name += promotion.getChar(config).toUpperCase()
            }
            name += checkForChecks(piece.side, piece.square.board)
            if (target != capture)
                name += " e.p."
            val undoReset = piece.square.board.resetMovesSinceLastCapture()
            val undo = {
                if (promotion != null)
                    piece.square.piece?.demote(piece)
                piece.move(origin)
                capturedPiece?.resurrect(c!!)
                undoReset()
                piece.force(pieceHasMoved)
            }
            return MoveData(piece, origin, target, name, undo)
        }
    }

    abstract class Special(piece: ChessPiece, display: ChessSquare, target: ChessSquare) :
        ChessMove(piece, display, target) {
        override val floor
            get() = Material.BLUE_CONCRETE
    }

}

fun getUniquenessCoordinate(piece: ChessPiece, target: ChessSquare): String {
    val board = target.board
    val pieces = board.pieces.filter { it.side == piece.side && it.type == piece.type }
    val consideredPieces =
        pieces.filter { p ->
            p.square.bakedMoves.orEmpty().any { it.target == target && board.isLegal(it) }
        }
    return when {
        consideredPieces.size == 1 -> ""
        consideredPieces.count { it.pos.file == piece.pos.file } == 1 -> piece.pos.fileStr
        consideredPieces.count { it.pos.rank == piece.pos.rank } == 1 -> piece.pos.rankStr
        else -> piece.pos.toString()
    }
}

fun checkForChecks(side: ChessSide, board: Chessboard): String {
    board.updateMoves()
    return when {
        board.piecesOf(!side).flatMap { board.getMoves(it.pos).filter(board::isLegal) }
            .isEmpty() -> "#"
        board.checkingMoves(side, board.piecesOf(!side).first { it.type == ChessType.KING }.square)
            .isNotEmpty() -> "+"
        else -> ""
    }
}

fun jumpTo(piece: ChessPiece, target: ChessSquare): List<ChessMove> =
    when (target.piece) {
        null -> listOf(ChessMove.Normal(piece, target))
        else -> listOf(
            ChessMove.Attack(
                piece,
                target,
                defensive = piece.side == target.piece?.side
            )
        )
    }

fun directionRay(piece: ChessPiece, dir: Pair<Int, Int>): List<ChessMove> {
    var p = piece.pos + dir
    val board = piece.square.board
    val ret = mutableListOf<ChessMove>()
    val actualBlocks = mutableListOf<ChessPosition>()
    val potentialBlocks = mutableListOf<ChessPosition>()
    ret.apply {
        while (p.isValid()) {
            while (p.isValid() && board[p] == null) {
                potentialBlocks += p
                add(ChessMove.Normal(piece, board.getSquare(p) ?: continue))
                p += dir
            }
            //TODO: ChessPiece.Type.KING shouldn't be mentioned here.
            val target = board[p]
            if (target?.type == ChessType.KING && target.side != piece.side) {
                add(
                    ChessMove.Attack(
                        piece,
                        target.square,
                        potentialBlocks = potentialBlocks.toList()
                    )
                )
                potentialBlocks += p
                p += dir
            } else
                break
        }
        while (p.isValid()) {
            val target = board[p] ?: break
            add(
                ChessMove.Attack(
                    piece,
                    target.square,
                    defensive = target.side == piece.side,
                    actualBlocks = actualBlocks.toList(),
                    potentialBlocks = potentialBlocks.toList()
                )
            )
            actualBlocks += p
            do {
                potentialBlocks += p
                p += dir
            } while (p.isValid() && board[p] == null)
        }
    }
    return ret
}

fun knightMovement(piece: ChessPiece) =
    rotationsOf(2, 1).map { piece.pos + it }.mapNotNull { piece.square.board.getSquare(it) }
        .flatMap { jumpTo(piece, it) }

fun rookMovement(piece: ChessPiece) =
    rotationsOf(1, 0).flatMap { directionRay(piece, it) }

fun bishopMovement(piece: ChessPiece) =
    rotationsOf(1, 1).flatMap { directionRay(piece, it) }

fun queenMovement(piece: ChessPiece) =
    (rotationsOf(1, 0) + rotationsOf(1, 1)).flatMap { directionRay(piece, it) }

fun kingMovement(piece: ChessPiece): List<ChessMove> {
    class Castles(
        piece: ChessPiece,
        val rook: ChessPiece,
        target: ChessSquare,
        val rookTargetSquare: ChessSquare,
        targetDisplay: Boolean
    ) : ChessMove.Special(
        piece,
        target,
        if (targetDisplay) target else rook.square
    ) {
        override val isValid: Boolean
            get() {
                return (between(piece.pos.file, target.pos.file) + listOf(
                    piece.pos.file,
                    target.pos.file
                ))
                    .mapNotNull { origin.board.getSquare(piece.pos.copy(file = it)) }
                    .all { origin.board.checkingPotentialMoves(!piece.side, it).isEmpty() }
            }

        override val canAttack
            get() = false

        override fun execute(config: ConfigManager): MoveData {
            var name = if (rook.pos.file < origin.pos.file) "O-O-O" else "O-O"
            val rookOrigin = rook.square
            ChessPiece.autoMove(mapOf(rook to rookTargetSquare, piece to target))
            name += checkForChecks(piece.side, origin.board)
            val undo = {
                ChessPiece.autoMove(mapOf(rook to rookOrigin, piece to origin))
                origin.board.undoMove()
                piece.force(false)
                rook.force(false)
            }
            return MoveData(piece, origin, target, name, undo)
        }
    }

    val board = piece.square.board
    val settings = board.game.settings
    val neighbours =
        piece.pos.neighbours().mapNotNull { board.getSquare(it) }.flatMap { jumpTo(piece, it) }
    val castles = mutableListOf<ChessMove>()
    if (!piece.hasMoved) {
        for (rook in board.pieces.filter { it.type == ChessType.ROOK }) {
            if (!rook.hasMoved && rook.side == piece.side && rook.pos.rank == piece.pos.rank) {
                if (settings.simpleCastling) {
                    val blocking = between(rook.pos.file, piece.pos.file)
                        .mapNotNull { board[piece.pos.copy(file = it)] }
                    if (blocking.isNotEmpty()) continue
                    if (rook.pos.file - piece.pos.file in listOf(-1, 1)) {
                        castles.add(Castles(piece, rook, rook.square, piece.square, true))
                    } else {
                        val targetSquare =
                            board.getSquare(
                                piece.pos.copy(file = piece.pos.file.towards(rook.pos.file, 2))
                            ) ?: continue
                        val rookTargetSquare =
                            board.getSquare(
                                piece.pos.copy(file = piece.pos.file.towards(rook.pos.file, 1))
                            ) ?: continue
                        castles.add(Castles(piece, rook, targetSquare, rookTargetSquare, true))
                    }
                } else {
                    if (rook.pos.file < piece.pos.file) { // Queenside
                        val blocking = between(min(rook.pos.file, 1), max(4, piece.pos.file))
                            .mapNotNull { board[piece.pos.copy(file = it)] }
                        if (blocking.any { it !in listOf(rook, piece) }) continue
                        val targetSquare = board.getSquare(piece.pos.copy(file = 2)) ?: continue
                        val rookTargetSquare = board.getSquare(piece.pos.copy(file = 3)) ?: continue
                        castles.add(
                            Castles(
                                piece, rook, targetSquare, rookTargetSquare,
                                piece.pos.file == 4 && rook.pos.file == 0
                            )
                        )
                    } else { // Kingside
                        val blocking = between(min(piece.pos.file, 4), max(7, rook.pos.file))
                            .mapNotNull { board[piece.pos.copy(file = it)] }
                        if (blocking.any { it !in listOf(rook, piece) }) continue
                        val targetSquare = board.getSquare(piece.pos.copy(file = 6)) ?: continue
                        val rookTargetSquare = board.getSquare(piece.pos.copy(file = 5)) ?: continue
                        castles.add(
                            Castles(
                                piece, rook, targetSquare, rookTargetSquare,
                                piece.pos.file == 4 && rook.pos.file == 7
                            )
                        )
                    }
                }
            }
        }
    }
    return neighbours + castles
}

fun pawnMovement(piece: ChessPiece): List<ChessMove> {
    val ret = mutableListOf<ChessMove>()
    val dir = Pair(0, piece.side.direction)
    val board = piece.square.board
    val origin = piece.pos
    val target1 = board.getSquare(origin + dir)
    if (target1 != null && target1.piece == null) {
        if ((origin + dir).rank !in listOf(0, 7))
            ret.add(ChessMove.Normal(piece, target1, defensive = false))
        else
            for (p in piece.type.promotions)
                ret.add(ChessMove.Normal(piece, target1, promotion = p, defensive = false))
        val target2 = board.getSquare(origin + dir * 2)
        if (target2 != null && target2.piece == null && !piece.hasMoved) {
            ret.add(ChessMove.Normal(piece, target2, defensive = false))
        }
    }
    for (s in listOf(-1, 1)) {
        val target3 = board.getSquare(origin + Pair(s, piece.side.direction)) ?: continue
        when {
            target3.piece == null || target3.piece?.side == piece.side ->
                ret.add(ChessMove.Attack(piece, target3, defensive = true))
            target3.pos.rank !in listOf(0, 7) ->
                ret.add(ChessMove.Attack(piece, target3))
            else ->
                for (p in piece.type.promotions)
                    ret.add(ChessMove.Attack(piece, target3, promotion = p))
        }
        val target4 = board.getSquare(origin.plusF(s))
        if (target3.piece == null && target4?.piece?.side == !piece.side) {
            val captured = board[origin.plusF(s)] ?: continue
            val lastMove = board.lastMove ?: continue
            if (captured.type == ChessType.PAWN &&
                lastMove.target == target4 &&
                lastMove.origin.pos == origin.plus(s, piece.side.direction * 2)
            ) {
                ret.add(ChessMove.Attack(piece, target3, target4))
            }
        }
    }
    return ret
}