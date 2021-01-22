package gregc.gregchess.chess

import gregc.gregchess.*
import gregc.gregchess.chess.component.Chessboard
import org.bukkit.Material
import kotlin.math.abs

sealed class ChessMove(val origin: ChessPosition, val target: ChessPosition) {
    fun display(game: ChessGame) {
        game.board.moveMarker(target, floor)
    }

    abstract val isValid: Boolean

    abstract val floor: Material

    abstract val canAttack: Boolean

    abstract fun execute(board: Chessboard)

    interface Promoting {
        val promotion: ChessType?
    }

    class Normal(
        origin: ChessPosition,
        target: ChessPosition,
        val defensive: Boolean = true,
        override val promotion: ChessType? = null
    ) : ChessMove(origin, target), Promoting {
        override val isValid: Boolean
            get() = true

        override val floor
            get() = if (promotion == null) Material.GREEN_CONCRETE else Material.BLUE_CONCRETE

        override val canAttack
            get() = defensive

        override fun execute(board: Chessboard) {
            if (!isValid) throw IllegalArgumentException()
            board.move(origin, target)
            if (promotion != null)
                board.promote(target, promotion)
        }
    }

    class Attack(
        origin: ChessPosition,
        target: ChessPosition,
        val capture: ChessPosition = target,
        val defensive: Boolean = false,
        val potentialBlocks: List<ChessPosition> = emptyList(),
        val actualBlocks: List<ChessPosition> = emptyList(),
        override val promotion: ChessType? = null
    ) : ChessMove(origin, target), Promoting {
        override val isValid: Boolean
            get() = actualBlocks.isEmpty() && !defensive

        override val canAttack
            get() = actualBlocks.isEmpty()

        override val floor
            get() = if (promotion == null) Material.RED_CONCRETE else Material.BLUE_CONCRETE

        override fun execute(board: Chessboard) {
            if (!isValid) throw IllegalArgumentException()
            board.capture(capture)
            board.move(origin, target)
            if (promotion != null)
                board.promote(target, promotion)
        }
    }

    abstract class Special(origin: ChessPosition, target: ChessPosition) : ChessMove(origin, target) {
        override val floor
            get() = Material.BLUE_CONCRETE
    }

}

fun jumpTo(origin: ChessPosition, board: Chessboard, target: ChessPosition): List<ChessMove> = when {
    board[target] == null -> listOf(ChessMove.Normal(origin, target))
    else -> listOf(ChessMove.Attack(origin, target, defensive = board[origin]?.side == board[target]?.side))
}

fun directionRay(origin: ChessPosition, board: Chessboard, dir: Pair<Int, Int>): List<ChessMove> {
    var p = origin + dir
    val side = board[origin]?.side ?: return emptyList()
    val ret = mutableListOf<ChessMove>()
    val actualBlocks = mutableListOf<ChessPosition>()
    val potentialBlocks = mutableListOf<ChessPosition>()
    ret.apply {
        while (p.isValid()) {
            while (p.isValid() && board[p] == null) {
                potentialBlocks += p
                add(ChessMove.Normal(origin, p))
                p += dir
            }
            //TODO: ChessPiece.Type.KING shouldn't be mentioned here.
            if (board[p]?.type == ChessType.KING && board[p]?.side != side) {
                add(
                    ChessMove.Attack(origin, p, potentialBlocks = potentialBlocks.toList())
                )
                potentialBlocks += p
                p += dir
            }
            else {
                break
            }
        }
        while (p.isValid()) {
            add(
                ChessMove.Attack(
                    origin,
                    p,
                    defensive = board[p]?.side == side,
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

fun knightMovement(origin: ChessPosition, board: Chessboard) =
    rotationsOf(2, 1).map { origin + it }.filter { it.isValid() }
        .flatMap { jumpTo(origin, board, it) }

fun rookMovement(origin: ChessPosition, board: Chessboard) =
    rotationsOf(1, 0).flatMap { directionRay(origin, board, it) }

fun bishopMovement(origin: ChessPosition, board: Chessboard) =
    rotationsOf(1, 1).flatMap { directionRay(origin, board, it) }

fun queenMovement(origin: ChessPosition, board: Chessboard) =
    (rotationsOf(1, 0) + rotationsOf(1, 1)).flatMap { directionRay(origin, board, it) }

fun kingMovement(origin: ChessPosition, board: Chessboard): List<ChessMove> {
    val neighbours = origin.neighbours().flatMap { jumpTo(origin, board, it) }
    val castles = mutableListOf<ChessMove>()
    val piece = board[origin] ?: return neighbours
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

                castles.add(object : ChessMove.Special(origin, origin.copy(file = newFile)) {
                    override val isValid: Boolean
                        get() {
                            return listOf(
                                piece.pos,
                                origin.copy(file = newRookFile),
                                origin.copy(file = newFile)
                            ).all { board.checkingPotentialMoves(!piece.side, it).isEmpty() }
                        }

                    override val canAttack
                        get() = false

                    override fun execute(board: Chessboard) {
                        if (!isValid) throw IllegalArgumentException()
                        if (dist == 1) {
                            board.swap(origin, target)
                        } else {
                            board.move(origin, target)
                            board.move(rook.pos, origin.copy(file = newRookFile))
                        }
                    }
                })
            }
        }
    }
    return neighbours + castles
}

fun pawnMovement(origin: ChessPosition, board: Chessboard): List<ChessMove> {
    val ret = mutableListOf<ChessMove>()
    val piece = board[origin] ?: return emptyList()
    val dir = Pair(0, piece.side.direction)
    if (board[origin + dir] == null) {
        if ((origin + dir).rank !in listOf(0, 7))
            ret.add(ChessMove.Normal(origin, origin + dir, defensive = false))
        else
            for (p in piece.type.promotions)
                ret.add(ChessMove.Normal(origin, origin + dir, promotion = p, defensive = false))
        if (board[origin + dir * 2] == null && !piece.hasMoved) {
            ret.add(ChessMove.Normal(origin, origin + dir * 2, defensive = false))
        }
    }
    for (s in listOf(-1, 1)) {
        val sd = Pair(s, piece.side.direction)
        when {
            board[origin + sd]?.side == piece.side || board[origin + sd] == null ->
                ret.add(ChessMove.Attack(origin, origin + sd, defensive = true))
            (origin + sd).rank !in listOf(0, 7) ->
                ret.add(ChessMove.Attack(origin, origin + sd))
            else ->
                for (p in piece.type.promotions)
                    ret.add(ChessMove.Attack(origin, origin + sd, promotion = p))
        }
        if (board[origin + sd] == null && board[origin.plusF(s)]?.side == !piece.side) {
            val captured = board[origin.plusF(s)] ?: continue
            val lastMove = board.lastMove ?: continue
            if (captured.type == ChessType.PAWN &&
                lastMove.target == origin.plusF(s) &&
                lastMove.origin == origin.plus(s, piece.side.direction * 2)
            ) {
                ret.add(ChessMove.Attack(origin, origin.plus(s, piece.side.direction), origin.plusF(s)))
            }
        }
    }
    return ret
}