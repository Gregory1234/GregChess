package gregc.gregchess.chess

import gregc.gregchess.Loc
import org.bukkit.Material
import kotlin.math.abs

class Chessboard(private val game: ChessGame) {
    private val boardState: MutableMap<ChessPosition, ChessPiece> = mutableMapOf()

    private val bakedMoves: MutableMap<ChessPosition, List<ChessMove>> = mutableMapOf()

    private var movesSinceLastCapture = 0
    private val boardHashes = mutableMapOf<Int, Int>()

    private val capturedPieces = mutableListOf<ChessPiece.Captured>()

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
        boardState[origin]?.let { it.playSound(game.world, it.type.moveSound) }
        boardState[target]?.let { it.playSound(game.world, it.type.moveSound) }
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
        val p = if (piece.type == ChessPiece.Type.PAWN)
            Pair(capturedPieces.count { it.side == piece.side && it.type == ChessPiece.Type.PAWN }, 1)
        else
            Pair(capturedPieces.count { it.side == piece.side && it.type != ChessPiece.Type.PAWN }, 0)
        val captured = piece.toCaptured(p)
        capturedPieces.add(captured)
        captured.render(game.world)
    }

    fun capture(pos: ChessPosition) = boardState[pos]?.let { capture(it) }

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

    fun clear() {
        pieces.forEach { it.hide(game.world) }
        for (i in 0 until 8 * 5) {
            for (j in 0 until 8 * 5) {
                game.world.getBlockAt(i, 100, j).type = Material.AIR
                game.world.getBlockAt(i, 101, j).type = Material.AIR
            }
        }
    }

    fun piecesOf(side: ChessSide) = boardState.values.filter { it.side == side }

    operator fun plusAssign(piece: ChessPiece) {
        boardState[piece.pos] = piece
        piece.render(game.world)
    }

    fun getMoves(pos: ChessPosition) = bakedMoves[pos].orEmpty()

    fun updateMoves() {
        bakedMoves.clear()
        boardState.forEach { (pos, piece) ->
            bakedMoves[pos] = piece.type.moveScheme(pos, this)
        }
    }

    val ChessMove.isLegal: Boolean
        get() {
            if (!isValid) return false
            val piece = this@Chessboard[origin] ?: return false
            if (piece.type == ChessPiece.Type.KING) {
                val checks = checkingPotentialMoves(!piece.side, target)
                return checks.isEmpty()
            }
            val myKing = piecesOf(piece.side).find { it.type == ChessPiece.Type.KING } ?: return false
            val checks = checkingMoves(!piece.side, myKing.pos)
            if (checks.any { target !in it.potentialBlocks && target != it.origin })
                return false
            val pins = pinningMoves(!piece.side, myKing.pos).filter { it.actualBlocks[0] == origin }
            if (pins.any { target !in it.potentialBlocks && target != it.origin })
                return false
            return true
        }

    fun pinningMoves(by: ChessSide, pos: ChessPosition) =
        piecesOf(by).flatMap { getMoves(it.pos) }.mapNotNull { it as? ChessMove.Attack }
            .filter { it.target == pos && !it.defensive && it.actualBlocks.size == 1 }

    fun checkingPotentialMoves(by: ChessSide, pos: ChessPosition) =
        piecesOf(by).flatMap { getMoves(it.pos) }.filter { it.target == pos }.filter { it.canAttack }

    fun checkingMoves(by: ChessSide, pos: ChessPosition) =
        piecesOf(by).flatMap { getMoves(it.pos) }.mapNotNull { it as? ChessMove.Attack }
            .filter { it.target == pos && it.isValid }


    fun setFromFEN(fen: String) {
        capturedPieces.forEach { it.hide(game.world) }
        capturedPieces.clear()
        pieces.forEach { it.hide(game.world) }
        boardState.clear()
        var x = 0
        var y = 7
        val parts = fen.split(" ")
        parts[0].split("/").forEach { rank ->
            rank.forEach {
                if (it in ('0'..'9')) {
                    x += it - '0'
                } else {
                    this += ChessPiece(
                        ChessPiece.Type.parseFromChar(it),
                        if (it.isUpperCase()) ChessSide.WHITE else ChessSide.BLACK,
                        ChessPosition(x, y),
                        when (it) {
                            'p' -> y != 6
                            'P' -> y != 1
                            else -> true
                        }
                    )
                    x++
                }
            }
            y--
            x = 0
        }
        parts[2].forEach { c ->
            when (c) {
                'k' -> {
                    val king = piecesOf(ChessSide.BLACK).find { it.type == ChessPiece.Type.KING }
                    val rook =
                        piecesOf(ChessSide.BLACK).findLast { it.type == ChessPiece.Type.ROOK && it.pos.file > king?.pos?.file ?: 0 }
                    if (king != null)
                        boardState[king.pos] = king.copy(hasMoved = false)
                    if (rook != null)
                        boardState[rook.pos] = rook.copy(hasMoved = false)
                }
                'K' -> {
                    val king = piecesOf(ChessSide.WHITE).find { it.type == ChessPiece.Type.KING }
                    val rook =
                        piecesOf(ChessSide.WHITE).findLast { it.type == ChessPiece.Type.ROOK && it.pos.file > king?.pos?.file ?: 0 }
                    if (king != null)
                        boardState[king.pos] = king.copy(hasMoved = false)
                    if (rook != null)
                        boardState[rook.pos] = rook.copy(hasMoved = false)
                }
                'q' -> {
                    val king = piecesOf(ChessSide.BLACK).find { it.type == ChessPiece.Type.KING }
                    val rook =
                        piecesOf(ChessSide.BLACK).find { it.type == ChessPiece.Type.ROOK && it.pos.file < king?.pos?.file ?: 0 }
                    if (king != null)
                        boardState[king.pos] = king.copy(hasMoved = false)
                    if (rook != null)
                        boardState[rook.pos] = rook.copy(hasMoved = false)
                }
                'Q' -> {
                    val king = piecesOf(ChessSide.WHITE).find { it.type == ChessPiece.Type.KING }
                    val rook =
                        piecesOf(ChessSide.WHITE).find { it.type == ChessPiece.Type.ROOK && it.pos.file < king?.pos?.file ?: 0 }
                    if (king != null)
                        boardState[king.pos] = king.copy(hasMoved = false)
                    if (rook != null)
                        boardState[rook.pos] = rook.copy(hasMoved = false)
                }
            }
        }

        if (parts[3] != "-") {
            val pos = ChessPosition.parseFromString(parts[2])
            val side = if (boardState[pos.plusR(1)] != null) ChessSide.WHITE else ChessSide.BLACK
            lastMove = ChessMove.Normal(pos.plusR(-side.direction), pos.plusR(side.direction))
        }

        movesSinceLastCapture = parts[4].toInt() + 1

        boardHashes.clear() // Nothing better to do here

        if (ChessSide.parseFromChar(parts[1][0]) != game.currentTurn) {
            game.nextTurn()
        } else
            updateMoves()
    }

    fun getFEN(): String = buildString {
        for (y in (0..7).reversed()) {
            var empty = 0
            for (x in (0..7)) {
                val piece = boardState[ChessPosition(x, y)]
                if (piece == null) {
                    empty++
                    continue
                }
                if (empty > 0) {
                    append(empty)
                }
                empty = 0
                val ch = piece.type.character
                append(if (piece.side == ChessSide.WHITE) ch.toUpperCase() else ch)
            }
            if (empty > 0) {
                append(empty)
            }
            if (y != 0)
                append("/")
        }
        append(" ")
        append(game.currentTurn.character)
        append(" ")
        val whiteKing = piecesOf(ChessSide.WHITE).find { it.type == ChessPiece.Type.KING }
        if (whiteKing != null && !whiteKing.hasMoved) {
            val whiteKingRook =
                piecesOf(ChessSide.WHITE).findLast { it.type == ChessPiece.Type.ROOK && it.pos.file > whiteKing.pos.file }
            if (whiteKingRook != null && !whiteKingRook.hasMoved)
                append("K")
            val whiteQueenRook =
                piecesOf(ChessSide.WHITE).findLast { it.type == ChessPiece.Type.ROOK && it.pos.file < whiteKing.pos.file }
            if (whiteQueenRook != null && !whiteQueenRook.hasMoved)
                append("Q")
        }
        val blackKing = piecesOf(ChessSide.BLACK).find { it.type == ChessPiece.Type.KING }
        if (blackKing != null && !blackKing.hasMoved) {
            val blackKingRook =
                piecesOf(ChessSide.BLACK).findLast { it.type == ChessPiece.Type.ROOK && it.pos.file > blackKing.pos.file }
            if (blackKingRook != null && !blackKingRook.hasMoved)
                append("k")
            val blackQueenRook =
                piecesOf(ChessSide.BLACK).findLast { it.type == ChessPiece.Type.ROOK && it.pos.file < blackKing.pos.file }
            if (blackQueenRook != null && !blackQueenRook.hasMoved)
                append("q")
        }
        append(" ")
        lastMove.also {
            if (it != null && it is ChessMove.Normal && boardState[it.target]?.type == ChessPiece.Type.PAWN && abs(it.origin.rank - it.target.rank) == 2) {
                append(it.origin.copy(rank = (it.origin.rank + it.target.rank) / 2))
            } else
                append("-")
        }
        append(" ")
        append(if (movesSinceLastCapture == 0) 0 else (movesSinceLastCapture - 1))
        append(" ")
        append(1)
    }

    private fun getBoardHash() = buildString {
        for (y in (0..7).reversed()) {
            for (x in (0..7)) {
                val piece = boardState[ChessPosition(x, y)]
                if (piece == null) {
                    append(" ")
                } else {
                    val ch = piece.type.character
                    append(if (piece.side == ChessSide.WHITE) ch.toUpperCase() else ch)
                }
            }
        }
        append(game.currentTurn)
        val whiteKing = piecesOf(ChessSide.WHITE).find { it.type == ChessPiece.Type.KING }
        if (whiteKing != null && !whiteKing.hasMoved) {
            val whiteKingRook =
                piecesOf(ChessSide.WHITE).findLast { it.type == ChessPiece.Type.ROOK && it.pos.file > whiteKing.pos.file }
            if (whiteKingRook != null && !whiteKingRook.hasMoved)
                append("K")
            val whiteQueenRook =
                piecesOf(ChessSide.WHITE).findLast { it.type == ChessPiece.Type.ROOK && it.pos.file < whiteKing.pos.file }
            if (whiteQueenRook != null && !whiteQueenRook.hasMoved)
                append("Q")
        }
        val blackKing = piecesOf(ChessSide.BLACK).find { it.type == ChessPiece.Type.KING }
        if (blackKing != null && !blackKing.hasMoved) {
            val blackKingRook =
                piecesOf(ChessSide.BLACK).findLast { it.type == ChessPiece.Type.ROOK && it.pos.file > blackKing.pos.file }
            if (blackKingRook != null && !blackKingRook.hasMoved)
                append("k")
            val blackQueenRook =
                piecesOf(ChessSide.BLACK).findLast { it.type == ChessPiece.Type.ROOK && it.pos.file < blackKing.pos.file }
            if (blackQueenRook != null && !blackQueenRook.hasMoved)
                append("q")
        }
        lastMove.also {
            if (it != null && it is ChessMove.Normal && boardState[it.target]?.type == ChessPiece.Type.PAWN && abs(it.origin.rank - it.target.rank) == 2) {
                append(it.origin.copy(rank = (it.origin.rank + it.target.rank) / 2))
            }
        }
    }

    fun checkForGameEnd() {
        if (addBoardHash() == 3)
            game.stop(ChessGame.EndReason.Repetition())
        if (++movesSinceLastCapture > 50)
            game.stop(ChessGame.EndReason.FiftyMoves())
        val whitePieces = piecesOf(ChessSide.WHITE)
        val blackPieces = piecesOf(ChessSide.BLACK)
        if (whitePieces.size == 1 && blackPieces.size == 1)
            game.stop(ChessGame.EndReason.InsufficientMaterial())
        if (whitePieces.size == 2 && whitePieces.any { it.type.minor } && blackPieces.size == 1)
            game.stop(ChessGame.EndReason.InsufficientMaterial())
        if (blackPieces.size == 2 && blackPieces.any { it.type.minor } && whitePieces.size == 1)
            game.stop(ChessGame.EndReason.InsufficientMaterial())
        if (game.config.relaxedInsufficientMaterial) {
            if (whitePieces.size == 2 && whitePieces.any { it.type.minor } && blackPieces.size == 2 && blackPieces.any { it.type.minor })
                game.stop(ChessGame.EndReason.InsufficientMaterial())
            if (whitePieces.size == 3 && whitePieces.count { it.type == ChessPiece.Type.KNIGHT } == 2 && blackPieces.size == 1)
                game.stop(ChessGame.EndReason.InsufficientMaterial())
            if (blackPieces.size == 3 && blackPieces.count { it.type == ChessPiece.Type.KNIGHT } == 2 && whitePieces.size == 1)
                game.stop(ChessGame.EndReason.InsufficientMaterial())
        }
    }

    private fun addBoardHash(): Int {
        val hash = getBoardHash()
        boardHashes[hash.hashCode()] = (boardHashes[hash.hashCode()] ?: 0).plus(1)
        return boardHashes[hash.hashCode()]!!
    }

    fun resetMovesSinceLastCapture() {
        movesSinceLastCapture = 0
    }
}