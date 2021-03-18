package gregc.gregchess.chess.component

import gregc.gregchess.Loc
import gregc.gregchess.chess.*
import gregc.gregchess.glog
import gregc.gregchess.star
import org.bukkit.Material
import org.bukkit.entity.Player
import kotlin.math.abs

class Chessboard(override val game: ChessGame, private val settings: Settings) : ChessGame.Component {
    data class Settings(private val initialFEN: String?, val chess960: Boolean = false) : ChessGame.ComponentSettings {
        override fun getComponent(game: ChessGame) = Chessboard(game, this)

        fun genFEN(): String {

            if (initialFEN != null)
                return initialFEN
            else if (!chess960)
                return "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
            else {
                val types = MutableList<Char?>(8) { null }
                types[(0..7).filter { it % 2 == 0 }.random()] = ChessType.BISHOP.standardChar
                types[(0..7).filter { it % 2 == 1 }.random()] = ChessType.BISHOP.standardChar
                types[(0..7).filter { types[it] == null }.random()] = ChessType.KNIGHT.standardChar
                types[(0..7).filter { types[it] == null }.random()] = ChessType.KNIGHT.standardChar
                types[(0..7).filter { types[it] == null }.random()] = ChessType.QUEEN.standardChar
                types[types.indexOf(null)] = ChessType.ROOK.standardChar
                types[types.indexOf(null)] = ChessType.KING.standardChar
                types[types.indexOf(null)] = ChessType.ROOK.standardChar
                val row = String(types.mapNotNull { it }.toCharArray())
                val pawns = ChessType.PAWN.standardChar.toString().repeat(8)
                return "$row/$pawns/8/8/8/8/${pawns.toUpperCase()}/${row.toUpperCase()} w KQkq - 0 1"
            }
        }

        companion object {

            fun init(settingsManager: SettingsManager) {
                settingsManager.registerComponent(
                    "Board", mapOf(
                        "normal" to Settings(null),
                        "chess960" to Settings(null, true)
                    )
                )
            }
        }
    }

    class Renderer(private val board: Chessboard) {
        fun getPos(loc: Loc) = ChessPosition((4 * 8 - 1 - loc.x).div(3), (loc.z - 8).div(3))

        fun getPieceLoc(pos: ChessPosition) = Loc(4 * 8 - 2 - pos.file * 3, 102, pos.rank * 3 + 8 + 1)

        fun getCapturedLoc(piece: ChessPiece.Captured): Loc {
            val cap =
                if (piece in board.capturedPieces) board.capturedPieces.takeWhile { it != piece } else board.capturedPieces
            val pos = if (piece.type == ChessType.PAWN)
                Pair(cap.count { it.side == piece.side && it.type == ChessType.PAWN }, 1)
            else
                Pair(cap.count { it.side == piece.side && it.type != ChessType.PAWN }, 0)
            return when (!piece.side) {
                ChessSide.WHITE -> Loc(4 * 8 - 1 - 2 * pos.first, 101, 8 - 3 - 2 * pos.second)
                ChessSide.BLACK -> Loc(8 + 2 * pos.first, 101, 8 * 4 + 2 + 2 * pos.second)
            }
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
        Pair(pos, ChessSquare(pos, this))
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

    var lastMove
        get() = moves.lastOrNull()
        set(v) {
            if (v != null)
                moves += v
            else
                moves.clear()
        }

    override fun start() {
        render()
        setFromFEN(settings.genFEN())
    }

    override fun update() {}
    override fun stop() {}
    override fun spectatorJoin(p: Player) {}
    override fun spectatorLeave(p: Player) {}
    override fun startPreviousTurn() {}
    override fun startTurn() {
        checkForGameEnd()
    }

    override fun previousTurn() {
        updateMoves()
    }

    override fun endTurn() {
        updateMoves()
        val num = "${fullMoveCounter + 1}."
        if (game.currentTurn == ChessSide.BLACK) {
            val wLast = (if (moves.size <= 1) "" else moves[moves.size - 2].name)
            val bLast = (lastMove?.name ?: "")
            game.realPlayers.forEach { p -> p.sendMessage("$num $wLast  | $bLast") }
            fullMoveCounter++
        } else if (piecesOf(!game.currentTurn).flatMap { p -> getMoves(p.pos).filter { it.isLegal } }.isEmpty()) {
            val wLast = (lastMove?.name ?: "")
            game.realPlayers.forEach { p -> p.sendMessage("$num $wLast  |") }
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
        glog.mid("Rendered chessboard", game.uuid)
    }

    override fun clear() {
        boardState.values.forEach { it.clear() }
        capturedPieces.forEach { it.hide() }
        for (i in 0 until 8 * 5) {
            for (j in 0 until 8 * 5) {
                game.world.getBlockAt(i, 100, j).type = Material.AIR
                game.world.getBlockAt(i, 101, j).type = Material.AIR
            }
        }
        glog.mid("Cleared chessboard", game.uuid)
    }

    fun piecesOf(side: ChessSide) = pieces.filter { it.side == side }

    operator fun plusAssign(captured: ChessPiece.Captured) {
        captured.render()
        capturedPieces += captured
        glog.low("Added captured", game.uuid, captured)
    }

    operator fun minusAssign(captured: ChessPiece.Captured) {
        capturedPieces -= captured
        captured.hide()
        glog.low("Removed captured", game.uuid, captured)
    }

    fun getMoves(pos: ChessPosition) = boardState[pos]?.bakedMoves.orEmpty()

    fun updateMoves() {
        boardState.forEach { (_, square) ->
            square.bakedMoves = square.piece?.let { it.type.moveScheme(it) }
        }
    }

    val ChessMove.isLegal: Boolean
        get() {
            if (!isValid) return false
            if (piece.type == ChessType.KING) {
                val checks = checkingPotentialMoves(!piece.side, target)
                return checks.isEmpty()
            }
            val myKing = piecesOf(piece.side).find { it.type == ChessType.KING } ?: return false
            val checks = checkingMoves(!piece.side, myKing.square)
            if (checks.any { target.pos !in it.potentialBlocks && target != it.origin })
                return false
            val pins = pinningMoves(!piece.side, myKing.square).filter { it.actualBlocks[0] == origin.pos }
            if (pins.any { target.pos !in it.potentialBlocks && target != it.origin })
                return false
            return true
        }

    fun pinningMoves(by: ChessSide, pos: ChessSquare) =
        piecesOf(by).flatMap { getMoves(it.pos) }.mapNotNull { it as? ChessMove.Attack }
            .filter { it.target == pos && !it.defensive && it.actualBlocks.size == 1 }

    fun checkingPotentialMoves(by: ChessSide, pos: ChessSquare) =
        piecesOf(by).flatMap { getMoves(it.pos) }.filter { it.target == pos }.filter { it.canAttack }

    fun checkingMoves(by: ChessSide, pos: ChessSquare) =
        piecesOf(by).flatMap { getMoves(it.pos) }.mapNotNull { it as? ChessMove.Attack }
            .filter { it.target == pos && it.isValid }


    fun setFromFEN(fen: String) {
        capturedPieces.forEach { it.hide() }
        capturedPieces.clear()
        boardState.values.forEach { it.clear() }
        var x = 0
        var y = 7
        val parts = fen.split(" ")
        parts[0].split("/").forEach { rank ->
            rank.forEach {
                if (it in ('0'..'9')) {
                    x += it - '0'
                } else {
                    val newPiece = ChessPiece(
                        ChessType.parseFromStandardChar(it),
                        if (it.isUpperCase()) ChessSide.WHITE else ChessSide.BLACK,
                        getSquare(ChessPosition(x, y))!!,
                        when (it) {
                            'p' -> y != 6
                            'P' -> y != 1
                            else -> true
                        }
                    )
                    this += newPiece
                    x++
                }
            }
            y--
            x = 0
        }
        parts[2].forEach { c ->
            when (c) {
                'k' -> {
                    val king = piecesOf(ChessSide.BLACK).find { it.type == ChessType.KING }
                    val rook =
                        piecesOf(ChessSide.BLACK).findLast { it.type == ChessType.ROOK && it.pos.file > king?.pos?.file ?: 0 }
                    king?.force(hasMoved = false)
                    rook?.force(hasMoved = false)
                }
                'K' -> {
                    val king = piecesOf(ChessSide.WHITE).find { it.type == ChessType.KING }
                    val rook =
                        piecesOf(ChessSide.WHITE).findLast { it.type == ChessType.ROOK && it.pos.file > king?.pos?.file ?: 0 }
                    king?.force(hasMoved = false)
                    rook?.force(hasMoved = false)
                }
                'q' -> {
                    val king = piecesOf(ChessSide.BLACK).find { it.type == ChessType.KING }
                    val rook =
                        piecesOf(ChessSide.BLACK).find { it.type == ChessType.ROOK && it.pos.file < king?.pos?.file ?: 0 }
                    king?.force(hasMoved = false)
                    rook?.force(hasMoved = false)
                }
                'Q' -> {
                    val king = piecesOf(ChessSide.WHITE).find { it.type == ChessType.KING }
                    val rook =
                        piecesOf(ChessSide.WHITE).find { it.type == ChessType.ROOK && it.pos.file < king?.pos?.file ?: 0 }
                    king?.force(hasMoved = false)
                    rook?.force(hasMoved = false)
                }
            }
        }

        boardHashes.clear()
        lastMove = null

        if (parts[3] != "-") {
            val pos = ChessPosition.parseFromString(parts[3])
            val piece = this[pos.plusR(1)] ?: this[pos.plusR(-1)]!!
            val origin = getSquare(piece.pos.plusR(-2 * piece.side.direction))!!
            val target = piece.square
            piece.move(origin)
            lastMove = MoveData(piece, origin, target, "") {}
            piece.move(target)
        }

        movesSinceLastCapture = parts[4].toInt()

        fullMoveCounter = parts[5].toInt().div(2)

        if (ChessSide.parseFromStandardChar(parts[1][0]) != game.currentTurn) {
            game.nextTurn()
        } else
            updateMoves()
    }

    fun getFEN(): String = buildString {
        for (y in (0..7).reversed()) {
            var empty = 0
            for (x in (0..7)) {
                val piece = this@Chessboard[ChessPosition(x, y)]
                if (piece == null) {
                    empty++
                    continue
                }
                if (empty > 0) {
                    append(empty)
                }
                empty = 0
                val ch = piece.type.standardChar
                append(if (piece.side == ChessSide.WHITE) ch.toUpperCase() else ch)
            }
            if (empty > 0) {
                append(empty)
            }
            if (y != 0)
                append("/")
        }
        append(" ")
        append(game.currentTurn.standardChar)
        append(" ")
        val whiteKing = piecesOf(ChessSide.WHITE).find { it.type == ChessType.KING }
        if (whiteKing != null && !whiteKing.hasMoved) {
            val whiteKingRook =
                piecesOf(ChessSide.WHITE).findLast { it.type == ChessType.ROOK && it.pos.file > whiteKing.pos.file }
            if (whiteKingRook != null && !whiteKingRook.hasMoved)
                append("K")
            val whiteQueenRook =
                piecesOf(ChessSide.WHITE).findLast { it.type == ChessType.ROOK && it.pos.file < whiteKing.pos.file }
            if (whiteQueenRook != null && !whiteQueenRook.hasMoved)
                append("Q")
        }
        val blackKing = piecesOf(ChessSide.BLACK).find { it.type == ChessType.KING }
        if (blackKing != null && !blackKing.hasMoved) {
            val blackKingRook =
                piecesOf(ChessSide.BLACK).findLast { it.type == ChessType.ROOK && it.pos.file > blackKing.pos.file }
            if (blackKingRook != null && !blackKingRook.hasMoved)
                append("k")
            val blackQueenRook =
                piecesOf(ChessSide.BLACK).findLast { it.type == ChessType.ROOK && it.pos.file < blackKing.pos.file }
            if (blackQueenRook != null && !blackQueenRook.hasMoved)
                append("q")
        }
        append(" ")
        lastMove.also {
            if (it != null && it.target.piece?.type == ChessType.PAWN
                && abs(it.origin.pos.rank - it.target.pos.rank) == 2
            ) {
                append(it.origin.pos.copy(rank = (it.origin.pos.rank + it.target.pos.rank) / 2))
            } else
                append("-")
        }
        append(" ")
        append(if (movesSinceLastCapture == 0) 0 else (movesSinceLastCapture - 1))
        append(" ")
        append(fullMoveCounter + 1)
    }

    private fun getBoardHash() = buildString {
        for (y in (0..7).reversed()) {
            for (x in (0..7)) {
                val piece = this@Chessboard[ChessPosition(x, y)]
                if (piece == null) {
                    append(" ")
                } else {
                    append(piece.side.toString() + piece.type.toString())
                }
            }
        }
        append(game.currentTurn)
        val whiteKing = piecesOf(ChessSide.WHITE).find { it.type == ChessType.KING }
        if (whiteKing != null && !whiteKing.hasMoved) {
            val whiteKingRook =
                piecesOf(ChessSide.WHITE).findLast { it.type == ChessType.ROOK && it.pos.file > whiteKing.pos.file }
            if (whiteKingRook != null && !whiteKingRook.hasMoved)
                append("K")
            val whiteQueenRook =
                piecesOf(ChessSide.WHITE).findLast { it.type == ChessType.ROOK && it.pos.file < whiteKing.pos.file }
            if (whiteQueenRook != null && !whiteQueenRook.hasMoved)
                append("Q")
        }
        val blackKing = piecesOf(ChessSide.BLACK).find { it.type == ChessType.KING }
        if (blackKing != null && !blackKing.hasMoved) {
            val blackKingRook =
                piecesOf(ChessSide.BLACK).findLast { it.type == ChessType.ROOK && it.pos.file > blackKing.pos.file }
            if (blackKingRook != null && !blackKingRook.hasMoved)
                append("k")
            val blackQueenRook =
                piecesOf(ChessSide.BLACK).findLast { it.type == ChessType.ROOK && it.pos.file < blackKing.pos.file }
            if (blackQueenRook != null && !blackQueenRook.hasMoved)
                append("q")
        }
        lastMove.also {
            if (it != null && it.target.piece?.type == ChessType.PAWN
                && abs(it.origin.pos.rank - it.target.pos.rank) == 2
            ) {
                append(it.origin.pos.copy(rank = (it.origin.pos.rank + it.target.pos.rank) / 2))
            }
        }
    }

    private fun checkForGameEnd() {
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

    fun undoMove() {
        movesSinceLastCapture--
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
            glog.mid("Undid last move", game.uuid, it)
        }
    }
}
