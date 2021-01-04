package gregc.gregchess.chess

import gregc.gregchess.Loc
import org.bukkit.Material

class Chessboard(private val game: ChessGame) {
    private val boardState: MutableMap<ChessPosition, ChessPiece> = (listOf(
        "wKe1",
        "bKe8",
        "wQd1",
        "bQd8",
        "wRa1",
        "wRh1",
        "bRa8",
        "bRh8",
        "wBc1",
        "wBf1",
        "bBc8",
        "bBf8",
        "wNb1",
        "wNg1",
        "bNb8",
        "bNg8"
    )
        .map { ChessPiece.parseFromString(it) } +
            (0..7).flatMap {
                listOf(
                    ChessPiece(ChessPiece.Type.PAWN, ChessSide.WHITE, ChessPosition(it, 1), false),
                    ChessPiece(ChessPiece.Type.PAWN, ChessSide.BLACK, ChessPosition(it, 6), false)
                )
            }).map { Pair(it.pos, it) }.toMap().toMutableMap()

    val pieces: List<ChessPiece>
        get() = boardState.values.toList()

    operator fun get(pos: ChessPosition) = boardState[pos]
    operator fun get(loc: Loc) = boardState[ChessPosition.fromLoc(loc)]

    var lastMove: ChessMove? = null

    fun move(piece: ChessPiece, target: ChessPosition) {
        boardState.remove(piece.pos)
        piece.hide(game.world)
        val newPiece = piece.copy(pos = target, hasMoved = true)
        boardState[target] = newPiece
        newPiece.playSound(game.world, piece.type.moveSound)
        newPiece.render(game.world)
    }

    fun move(origin: ChessPosition, target: ChessPosition) =
        boardState[origin]?.let { move(it, target) }

    fun swap(origin: ChessPosition, target: ChessPosition) {
        val a1 = boardState[origin]
        val a2 = boardState[target]
        if (a1 != null && a2 != null) {
            boardState[target] = a1.copy(pos = target, hasMoved = true)
            boardState[origin] = a2.copy(pos = origin, hasMoved = true)
        } else if (a1 != null && a2 == null) {
            boardState[target] = a1.copy(pos = target, hasMoved = true)
            boardState.remove(origin)
        } else if (a1 == null && a2 != null) {
            boardState[origin] = a2.copy(pos = origin, hasMoved = true)
            boardState.remove(target)
        }
        boardState[origin]?.let {it.playSound(game.world, it.type.moveSound)}
        boardState[target]?.let {it.playSound(game.world, it.type.moveSound)}
    }

    fun pickUp(piece: ChessPiece) {
        piece.playSound(game.world, piece.type.pickUpSound)
        piece.hide(game.world)
    }

    fun placeDown(piece: ChessPiece) {
        piece.playSound(game.world, piece.type.moveSound)
        piece.render(game.world)
    }

    fun capture(piece: ChessPiece) {
        piece.playSound(game.world, piece.type.captureSound)
        piece.hide(game.world)
        boardState.remove(piece.pos)
    }

    fun capture(pos: ChessPosition) = boardState[pos]?.let { capture(it) }

    fun capture(loc: Loc) = this[loc]?.let { capture(it) }

    fun promote(piece: ChessPiece, promotion: ChessPiece.Type) {
        if (promotion !in piece.promotions)
            return
        piece.hide(game.world)
        val newPiece = piece.copy(type = promotion, hasMoved = false)
        boardState[piece.pos] = newPiece
        newPiece.render(game.world)
    }

    fun promote(pos: ChessPosition, promotion: ChessPiece.Type) =
        boardState[pos]?.let { promote(it, promotion) }

    fun render() {
        for (i in 0 until 8 * 5) {
            for (j in 0 until 8 * 5) {
                game.world.getBlockAt(i, 100, j).type = Material.DARK_OAK_PLANKS
                if (i in 8 - 1..8 * 4 && j in 8 - 1..8 * 4) {
                    game.world.getBlockAt(i, 101, j).type = Material.DARK_OAK_PLANKS
                }
            }
        }
        for (i in 0 until 8) {
            for (j in 0 until 8) {
                ChessPosition(i, j).clear(game.world)
            }
        }
        pieces.forEach { it.render(game.world) }
    }

    fun getPositionHash(): Long {
        val board: Array<Array<ChessPiece?>> = Array(8) { Array(8) { null } }
        var possum = 0
        boardState.values.forEach {
            board[it.pos.file][it.pos.rank] = it; possum += it.pos.file * 256 + it.pos.rank
        }
        val content =
            buildString { board.forEach { l -> l.forEach { append("${it?.type}${it?.side}") } } }
        return content.hashCode().toLong() * 65536 + possum
    }

    fun piecesOf(side: ChessSide) = boardState.values.filter { it.side == side }

    operator fun plusAssign(piece: ChessPiece) {
        boardState[piece.pos] = piece
        piece.render(game.world)
    }

    //TODO: Pinned pieces shouldn't be able to attack(?).

    fun attackedPositions(by: ChessSide) =
        piecesOf(by).flatMap { it.getMoves(this) }.filter { it.couldAttack }.map { it.target }

    fun attackingMoves(by: ChessSide, pos: ChessPosition) =
        piecesOf(by).flatMap { it.getMoves(this) }.filter { it.target == pos }.mapNotNull { it as? ChessMove.Attack }

    fun pinningMoves(by: ChessSide, pos: ChessPosition) =
        piecesOf(by).flatMap { it.getMoves(this) }.mapNotNull { it as? ChessMove.XRayAttack }
            .filter { it.target == pos }

    fun checkedPositions(by: ChessSide) =
        piecesOf(by).filter { it.type != ChessPiece.Type.KING }.flatMap { it.getMoves(this) }
            .filter { it.couldAttack }.map { it.target }

}