package gregc.gregchess.chess

import gregc.gregchess.between
import gregc.gregchess.rotationsOf
import gregc.gregchess.times
import gregc.gregchess.towards
import org.bukkit.Material
import kotlin.math.abs

sealed class ChessMove(
    val origin: ChessPosition,
    val target: ChessPosition,
    private val floor: Material,
    val executable: Boolean = true,
    val couldAttack: Boolean = true
) {
    fun display(game: ChessGame) {
        target.fillFloor(game.world, floor)
    }

    abstract fun execute(board: Chessboard)

    class Normal(
        origin: ChessPosition,
        target: ChessPosition,
        val promotion: ChessPiece.Type? = null,
        couldAttack: Boolean = true
    ) : ChessMove(
        origin,
        target,
        if (promotion == null) Material.GREEN_CONCRETE else Material.BLUE_CONCRETE,
        couldAttack = couldAttack
    ) {
        override fun execute(board: Chessboard) {
            board.move(origin, target)
            if (promotion != null)
                board.promote(target, promotion)
        }
    }

    class Attack(
        origin: ChessPosition,
        target: ChessPosition,
        val blocks: List<ChessPosition>,
        val promotion: ChessPiece.Type? = null
    ) : ChessMove(
        origin,
        target,
        if (promotion == null) Material.RED_CONCRETE else Material.BLUE_CONCRETE
    ) {
        override fun execute(board: Chessboard) {
            board.capture(target)
            board.move(origin, target)
            if (promotion != null)
                board.promote(target, promotion)
        }
    }

    class Defence(origin: ChessPosition, target: ChessPosition) :
        ChessMove(origin, target, Material.GRAY_CONCRETE, false) {
        override fun execute(board: Chessboard) {}
    }

    class XRayAttack(origin: ChessPosition, val pinned: ChessPosition, target: ChessPosition, val blocks: List<ChessPosition>) :
        ChessMove(origin, target, Material.GRAY_CONCRETE, false, false) {
        override fun execute(board: Chessboard) {}
    }

    abstract class Special(
        origin: ChessPosition,
        target: ChessPosition,
        couldAttack: Boolean = true
    ) :
        ChessMove(origin, target, Material.BLUE_CONCRETE, couldAttack = couldAttack)
}

fun jumpTo(origin: ChessPosition, board: Chessboard, target: ChessPosition) = when {
    board[target] == null -> listOf(ChessMove.Normal(origin, target))
    board[origin]?.side == board[target]?.side -> listOf(ChessMove.Defence(origin, target))
    else -> listOf(ChessMove.Attack(origin, target, emptyList()))
}

fun directionRay(origin: ChessPosition, board: Chessboard, dir: Pair<Int, Int>): List<ChessMove> {
    var p = origin + dir
    val side = board[origin]?.side ?: return emptyList()
    val ret = mutableListOf<ChessMove>()
    val blocks = mutableListOf<ChessPosition>()
    ret.apply {
        while (p.isValid() && board[p] == null) {
            add(ChessMove.Normal(origin, p))
            blocks += p
            p += dir
        }
        if (p.isValid()) {
            if (board[p]?.side == side) add(ChessMove.Defence(origin, p))
            else {
                add(ChessMove.Attack(origin, p, blocks))
                val blocksRest = mutableListOf<ChessPosition>()
                val pinned = p
                p += dir
                while (p.isValid() && board[p] == null) {
                    p += dir
                    blocksRest += p
                }
                if (p.isValid() && board[p]?.side != side) {
                    add(ChessMove.XRayAttack(origin, pinned, p, blocks + blocksRest))
                }
            }
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
        for (rook in board.pieces.filter { it.type == ChessPiece.Type.ROOK }) {
            if (!rook.hasMoved && rook.side == piece.side && rook.pos.rank == origin.rank) {
                val between = between(origin.file, rook.pos.file).map { origin.copy(file = it) }
                val attacked = board.checkedPositions(!piece.side)
                if (!between.all { board[it] == null && it !in attacked }) continue

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

                castles.add(object : ChessMove.Special(origin, origin.copy(file = newFile), false) {
                    override fun execute(board: Chessboard) {
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
            ret.add(ChessMove.Normal(origin, origin + dir, couldAttack = false))
        else
            for (p in piece.promotions)
                ret.add(ChessMove.Normal(origin, origin + dir, p, couldAttack = false))
        if (board[origin + dir * 2] == null && !piece.hasMoved) {
            ret.add(ChessMove.Normal(origin, origin + dir * 2, couldAttack = false))
        }
    }
    for (s in listOf(-1, 1)) {
        val sd = Pair(s, piece.side.direction)
        if (board[origin + sd] != null) {
            when {
                board[origin + sd]?.side == piece.side ->
                    ret.add(ChessMove.Defence(origin, origin + sd))
                (origin + sd).rank !in listOf(0, 7) ->
                    ret.add(ChessMove.Attack(origin, origin + sd, emptyList()))
                else ->
                    for (p in piece.promotions)
                        ret.add(ChessMove.Attack(origin, origin + sd, emptyList(), p))
            }
        }
        if (board[origin + sd] == null && board[origin + Pair(s, 0)]?.side == !piece.side) {
            val captured = board[origin + Pair(s, 0)] ?: continue
            val lastMove = board.lastMove ?: continue
            if (captured.type == ChessPiece.Type.PAWN &&
                lastMove.target == origin + Pair(s, 0) &&
                lastMove.origin == origin + Pair(s, piece.side.direction * 2)
            ) {
                ret.add(object: ChessMove.Special(origin, origin+sd){
                    override fun execute(board: Chessboard) {
                        board.move(origin, target)
                        board.capture(origin + Pair(s, 0))
                    }
                })
            }
        }
    }
    return ret
}