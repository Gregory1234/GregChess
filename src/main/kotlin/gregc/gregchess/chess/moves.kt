package gregc.gregchess.chess

import gregc.gregchess.*
import org.bukkit.Material
import kotlin.math.abs

sealed class ChessMove(val piece: ChessPiece, val target: ChessSquare) {
    val origin = piece.square

    fun render() {
        target.moveMarker = floor
        target.render()
    }

    fun clear() {
        target.moveMarker = null
        target.render()
    }

    fun renderDone() {
        origin.previousMoveMarker = Material.BROWN_CONCRETE
        origin.render()
        target.previousMoveMarker = Material.ORANGE_CONCRETE
        target.render()
    }

    fun clearDone() {
        origin.previousMoveMarker = null
        origin.render()
        target.previousMoveMarker = null
        target.render()
    }

    abstract val isValid: Boolean

    abstract val floor: Material

    abstract val canAttack: Boolean

    abstract fun execute()

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

        override fun execute() {
            if (!isValid) throw IllegalArgumentException()
            piece.move(target)
            if (promotion != null)
                piece.promote(promotion)
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

        override fun execute() {
            if (!isValid) throw IllegalArgumentException()
            capture.piece?.capture()
            piece.move(target)
            if (promotion != null)
                piece.promote(promotion)
        }
    }

    abstract class Special(piece: ChessPiece, target: ChessSquare) : ChessMove(piece, target) {
        override val floor
            get() = Material.BLUE_CONCRETE
    }

}

fun jumpTo(piece: ChessPiece, target: ChessSquare): List<ChessMove> =
    when (target.piece) {
        null -> listOf(ChessMove.Normal(piece, target))
        else -> listOf(ChessMove.Attack(piece, target, defensive = piece.side == target.piece?.side))
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
                add(ChessMove.Attack(piece, target.square, potentialBlocks = potentialBlocks.toList()))
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
    val board = piece.square.board
    val origin = piece.pos
    val neighbours = origin.neighbours().mapNotNull { board.getSquare(it) }.flatMap { jumpTo(piece, it) }
    val castles = mutableListOf<ChessMove>()
    if (!piece.hasMoved) {
        for (rook in board.pieces.filter { it.type == ChessType.ROOK }) {
            if (!rook.hasMoved && rook.side == piece.side && rook.pos.rank == origin.rank) {
                val between = between(origin.file, rook.pos.file).map { origin.copy(file = it) }

                if (!between.all { board[it] == null }) continue

                val dist = abs(origin.file - rook.pos.file)

                val newFile: Int
                val newRookFile: Int
                if (dist == 1) {
                    newFile = rook.pos.file
                    newRookFile = origin.file
                } else if (dist > 1) {
                    newFile = origin.file.towards(rook.pos.file, 2)
                    newRookFile = origin.file.towards(rook.pos.file, 1)
                } else
                    continue

                val targetSquare = board.getSquare(origin.copy(file = newFile)) ?: continue
                val targetRookSquare = board.getSquare(origin.copy(file = newRookFile)) ?: continue

                castles.add(object : ChessMove.Special(piece, targetSquare) {
                    override val isValid: Boolean
                        get() {
                            return listOf(piece.square, targetRookSquare, targetSquare)
                                .all { piece.square.board.checkingPotentialMoves(!piece.side, it).isEmpty() }
                        }

                    override val canAttack
                        get() = false

                    override fun execute() {
                        if (!isValid) throw IllegalArgumentException()
                        if (dist == 1) {
                            piece.swap(rook)
                        } else {
                            piece.move(targetSquare)
                            rook.move(targetRookSquare)
                        }
                    }
                })
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