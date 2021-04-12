package gregc.gregchess.chess.component

import gregc.gregchess.*
import gregc.gregchess.chess.*
import org.bukkit.Material
import org.bukkit.Sound
import java.lang.NullPointerException
import java.util.*
import kotlin.math.abs

class Chessboard(private val game: ChessGame, settings: Settings) {
    data class Settings(val initialFEN: FEN?, val chess960: Boolean = false) {
        fun getComponent(game: ChessGame) = Chessboard(game, this)

        fun genFEN() = initialFEN ?: if (!chess960) FEN() else FEN.generateChess960()

        companion object {

            private val normal = Settings(null)

            operator fun get(name: String?) = when (name) {
                "normal" -> normal
                null -> normal
                "chess960" -> Settings(null, true)
                else -> {
                    if (name.startsWith("fen ")) try {
                        Settings(FEN.parseFromString(name.drop(4)))
                    } catch (e: IllegalArgumentException) {
                        glog.warn("Chessboard configuration ${name.drop(4)} is in a wrong format, defaulted to normal!")
                        normal
                    } else {
                        glog.warn("Invalid chessboard configuration $name, defaulted to normal!")
                        normal
                    }
                }
            }

        }
    }

    class Renderer(private val board: Chessboard) {
        fun getPos(loc: Loc) =
            ChessPosition(Math.floorDiv(4 * 8 - 1 - loc.x, 3), Math.floorDiv(loc.z - 8, 3))

        fun getPieceLoc(pos: ChessPosition) =
            Loc(4 * 8 - 2 - pos.file * 3, 102, pos.rank * 3 + 8 + 1)

        fun getCapturedLoc(piece: ChessPiece.Captured): Loc {
            val cap = board.capturedPieces.takeWhile { it != piece }
            val pos = if (piece.type == ChessType.PAWN)
                Pair(cap.count { it.by == piece.by && it.type == ChessType.PAWN }, 1)
            else
                Pair(cap.count { it.by == piece.by && it.type != ChessType.PAWN }, 0)
            return when (piece.by) {
                ChessSide.WHITE -> Loc(4 * 8 - 1 - 2 * pos.first, 101, 8 - 3 - 2 * pos.second)
                ChessSide.BLACK -> Loc(8 + 2 * pos.first, 101, 8 * 4 + 2 + 2 * pos.second)
            }
        }

        fun renderPiece(loc: Loc, structure: List<Material>) {
            structure.forEachIndexed { i, m ->
                board.game.world.getBlockAt(loc.copy(y = loc.y + i)).type = m
            }
        }

        fun playPieceSound(pos: ChessPosition, sound: Sound) {
            getPieceLoc(pos).toLocation(board.game.world).playSound(sound)
        }

        fun fillFloor(pos: ChessPosition, floor: Material) {
            val (x, y, z) = getPieceLoc(pos)
            (-1..1).star(-1..1) { i, j ->
                board.game.world.getBlockAt(x + i, y - 1, z + j).type = floor
            }
        }
    }

    val renderer = Renderer(this)

    private val boardState = (0..7).star(0..7) { i, j ->
        val pos = ChessPosition(i, j)
        Pair(pos, ChessSquare(pos, game))
    }.toMap()

    private var movesSinceLastCapture = 0
    private var fullMoveCounter = 0
    private val boardHashes = mutableMapOf<Int, Int>()

    private val capturedPieces = mutableListOf<ChessPiece.Captured>()

    val pieces: List<ChessPiece>
        get() = boardState.values.mapNotNull { it.piece }

    operator fun get(pos: ChessPosition) = getSquare(pos)?.piece

    operator fun plusAssign(piece: ChessPiece) {
        piece.square.piece = piece
    }

    operator fun get(loc: Loc) = this[renderer.getPos(loc)]

    fun getSquare(pos: ChessPosition) = boardState[pos]

    fun getSquare(loc: Loc) = getSquare(renderer.getPos(loc))

    private val moves: MutableList<MoveData> = mutableListOf()

    val moveHistory: List<MoveData>
        get() = moves

    val initialFEN = settings.genFEN()

    var lastMove
        get() = moves.lastOrNull()
        set(v) {
            if (v != null)
                moves += v
            else
                moves.clear()
        }

    fun start() {
        render()
        setFromFEN(initialFEN)
    }

    fun startTurn() {
        checkForGameEnd()
    }

    fun previousTurn() {
        updateMoves()
    }

    fun endTurn() {
        updateMoves()
        val num = "${fullMoveCounter}."
        if (game.currentTurn == ChessSide.BLACK) {
            val wLast = (if (moves.size <= 1) "" else moves[moves.size - 2].name)
            val bLast = (lastMove?.name ?: "")
            game.forEachPlayer { p -> p.sendMessage("$num $wLast  | $bLast") }
            fullMoveCounter++
        } else if (piecesOf(!game.currentTurn).flatMap { p -> getMoves(p.pos).filter { isLegal(it) } }
                .isEmpty()) {
            val wLast = (lastMove?.name ?: "")
            game.forEachPlayer { p -> p.sendMessage("$num $wLast  |") }
        }
    }

    private fun render() {
        for (i in 0 until 8 * 5) {
            for (j in 0 until 8 * 5) {
                game.world.getBlockAt(i, 100, j).type = Material.DARK_OAK_PLANKS
                if (i in 8 - 1..8 * 4 && j in 8 - 1..8 * 4) {
                    game.world.getBlockAt(i, 101, j).type = Material.DARK_OAK_PLANKS
                }
            }
        }
        boardState.values.forEach { it.render() }
        glog.mid("Rendered chessboard", game.uniqueId)
    }

    fun clear() {
        boardState.values.forEach { it.clear() }
        capturedPieces.forEach { it.hide() }
        for (i in 0 until 8 * 5) {
            for (j in 0 until 8 * 5) {
                game.world.getBlockAt(i, 100, j).type = Material.AIR
                game.world.getBlockAt(i, 101, j).type = Material.AIR
            }
        }
        glog.mid("Cleared chessboard", game.uniqueId)
    }

    operator fun contains(pieceUniqueId: UUID) = pieces.any { it.uniqueId == pieceUniqueId }
    operator fun get(pieceUniqueId: UUID) = pieces.firstOrNull { it.uniqueId == pieceUniqueId }

    fun piecesOf(side: ChessSide) = pieces.filter { it.side == side }

    operator fun plusAssign(captured: ChessPiece.Captured) {
        captured.render()
        capturedPieces += captured
        glog.low("Added captured", game.uniqueId, captured)
    }

    operator fun minusAssign(captured: ChessPiece.Captured) {
        capturedPieces -= captured
        captured.hide()
        glog.low("Removed captured", game.uniqueId, captured)
    }

    fun getMoves(pos: ChessPosition) = boardState[pos]?.bakedMoves.orEmpty()

    fun updateMoves() {
        boardState.forEach { (_, square) ->
            square.bakedMoves = square.piece?.let { it.type.moveScheme(it) }
        }
    }

    fun isLegal(move: ChessMove): Boolean = move.run {
        if (!isValid) return false
        if (piece.type == ChessType.KING) {
            val checks = checkingPotentialMoves(!piece.side, target)
            return checks.isEmpty()
        }
        val myKing = try {
            piecesOf(piece.side).find { it.type == ChessType.KING }!!
        } catch (e: NullPointerException) {
            game.stop(ChessGame.EndReason.Error(e))
            throw e
        }
        val checks = checkingMoves(!piece.side, myKing.square)
        if (checks.any { target.pos !in it.potentialBlocks && target != it.origin })
            return false
        val pins =
            pinningMoves(!piece.side, myKing.square).filter { it.actualBlocks[0] == origin.pos }
        if (pins.any { target.pos !in it.potentialBlocks && target != it.origin })
            return false
        return true
    }

    fun pinningMoves(by: ChessSide, pos: ChessSquare) =
        piecesOf(by).flatMap { getMoves(it.pos) }.mapNotNull { it as? ChessMove.Attack }
            .filter { it.target == pos && !it.defensive && it.actualBlocks.size == 1 }

    fun checkingPotentialMoves(by: ChessSide, pos: ChessSquare) =
        piecesOf(by).flatMap { getMoves(it.pos) }.filter { it.target == pos }
            .filter { it.canAttack }

    fun checkingMoves(by: ChessSide, pos: ChessSquare) =
        piecesOf(by).flatMap { getMoves(it.pos) }.mapNotNull { it as? ChessMove.Attack }
            .filter { it.target == pos && it.isValid }


    fun setFromFEN(fen: FEN) {
        capturedPieces.forEach { it.hide() }
        capturedPieces.clear()
        boardState.values.forEach { it.clear() }
        fen.forEachSquare { pos, tr ->
            if (tr != null) {
                val (t, s, hm) = tr
                this += ChessPiece(t, s, getSquare(pos)!!, hm)
            }
        }
        if (fen.enPassantSquare != null) {
            val pos = fen.enPassantSquare
            val piece = this[pos.plusR(1)] ?: this[pos.plusR(-1)]!!
            val origin = getSquare(piece.pos.plusR(-2 * piece.side.direction))!!
            val target = piece.square
            piece.move(origin)
            lastMove = MoveData(piece, origin, target, "", "") {}
            piece.move(target)
        }

        movesSinceLastCapture = fen.halfmoveClock

        fullMoveCounter = fen.fullmoveClock

        if (fen.currentTurn != game.currentTurn)
            game.nextTurn()
        else
            updateMoves()

        boardHashes.clear()
        addBoardHash()
    }

    fun getFEN(): FEN {
        fun castling(side: ChessSide) =
            if (pieces.any { it.type == ChessType.KING && it.side == side && !it.hasMoved })
                pieces.filter { it.type == ChessType.ROOK && it.side == side && !it.hasMoved }
                    .map { it.pos.file }
            else emptyList()

        return FEN(
            (0..7).reversed().joinToString("/") {
                var e = 0
                buildString {
                    for (i in 0..7) {
                        val piece = this@Chessboard[ChessPosition(i, it)]
                        if (piece == null)
                            e++
                        else {
                            if (e != 0)
                                append(e)
                            e = 0
                            append(if (piece.side == ChessSide.WHITE) piece.type.char.toUpperCase() else piece.type.char)
                        }
                    }
                    if (e == 8)
                        append(e)
                }
            },
            game.currentTurn,
            castling(ChessSide.WHITE),
            castling(ChessSide.BLACK),
            lastMove?.let {
                if (it.target.piece?.type == ChessType.PAWN
                    && abs(it.origin.pos.rank - it.target.pos.rank) == 2
                ) {
                    it.origin.pos.copy(rank = (it.origin.pos.rank + it.target.pos.rank) / 2)
                } else
                    null
            },
            movesSinceLastCapture,
            fullMoveCounter
        )
    }

    private fun getBoardHash() = getFEN().toHash()

    private fun checkForGameEnd() {
        if (addBoardHash() == 3)
            game.stop(ChessGame.EndReason.Repetition())
        if (movesSinceLastCapture >= 100)
            game.stop(ChessGame.EndReason.FiftyMoves())
        val whitePieces = piecesOf(ChessSide.WHITE)
        val blackPieces = piecesOf(ChessSide.BLACK)
        if (whitePieces.size == 1 && blackPieces.size == 1)
            game.stop(ChessGame.EndReason.InsufficientMaterial())
        if (whitePieces.size == 2 && whitePieces.any { it.type.minor } && blackPieces.size == 1)
            game.stop(ChessGame.EndReason.InsufficientMaterial())
        if (blackPieces.size == 2 && blackPieces.any { it.type.minor } && whitePieces.size == 1)
            game.stop(ChessGame.EndReason.InsufficientMaterial())
        if (game.settings.relaxedInsufficientMaterial) {
            if (whitePieces.size == 2 && whitePieces.any { it.type.minor } && blackPieces.size == 2 && blackPieces.any { it.type.minor })
                game.stop(ChessGame.EndReason.InsufficientMaterial())
            if (whitePieces.size == 3 && whitePieces.count { it.type == ChessType.KNIGHT } == 2 && blackPieces.size == 1)
                game.stop(ChessGame.EndReason.InsufficientMaterial())
            if (blackPieces.size == 3 && blackPieces.count { it.type == ChessType.KNIGHT } == 2 && whitePieces.size == 1)
                game.stop(ChessGame.EndReason.InsufficientMaterial())
        }
    }

    private fun addBoardHash(): Int {
        val hash = getBoardHash()
        boardHashes[hash.hashCode()] = (boardHashes[hash.hashCode()] ?: 0).plus(1)
        return boardHashes[hash.hashCode()]!!
    }

    fun resetMovesSinceLastCapture(): () -> Unit {
        val m = movesSinceLastCapture
        movesSinceLastCapture = 0
        return {
            movesSinceLastCapture = m
        }
    }

    fun increaseMovesSinceLastCapture(): () -> Unit {
        movesSinceLastCapture++
        return {
            movesSinceLastCapture--
        }
    }

    fun undoLastMove() {
        lastMove?.let {
            it.clear()
            val hash = getBoardHash()
            if (boardHashes[hash.hashCode()] != null)
                boardHashes[hash.hashCode()] = boardHashes[hash.hashCode()]!! - 1
            it.undo()
            if (game.currentTurn == ChessSide.WHITE)
                fullMoveCounter--
            moves.removeLast()
            lastMove?.render()
            game.previousTurn()
            glog.mid("Undid last move", game.uniqueId, it)
        }
    }
}
