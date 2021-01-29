package gregc.gregchess.chess.component

import gregc.gregchess.Loc
import gregc.gregchess.chess.*
import gregc.gregchess.getBlockAt
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
                val types = MutableList<ChessType?>(8) { null }
                types[(0..7).filter { it % 2 == 0 }.random()] = ChessType.BISHOP
                types[(0..7).filter { it % 2 == 1 }.random()] = ChessType.BISHOP
                types[(0..7).filter { types[it] == null }.random()] = ChessType.KNIGHT
                types[(0..7).filter { types[it] == null }.random()] = ChessType.KNIGHT
                types[(0..7).filter { types[it] == null }.random()] = ChessType.QUEEN
                types[types.indexOf(null)] = ChessType.ROOK
                types[types.indexOf(null)] = ChessType.KING
                types[types.indexOf(null)] = ChessType.ROOK
                val row = String(types.mapNotNull { it?.character }.toCharArray())
                return "${row}/pppppppp/8/8/8/8/PPPPPPPP/${row.toUpperCase()} w KQkq - 0 1"
            }
        }

        companion object {

            fun init() {
                ChessGame.Settings.registerComponent(
                    "Board", mapOf(
                        "normal" to Settings(null),
                        "chess960" to Settings(null, true)
                    )
                )
            }
        }
    }

    class Renderer(private val board: Chessboard){
        fun getPos(loc: Loc) = ChessPosition((4 * 8 - 1 - loc.x).div(3), (loc.z - 8).div(3))

        fun getPieceLoc(pos: ChessPosition) = Loc(4 * 8 - 2 - pos.file * 3, 102, pos.rank * 3 + 8 + 1)

        fun getCapturedLoc(piece: ChessPiece.Captured): Loc {
            val pos = if (piece.type == ChessType.PAWN)
                Pair(board.capturedPieces.count { it.side == piece.side && it.type == ChessType.PAWN }, 1)
            else
                Pair(board.capturedPieces.count { it.side == piece.side && it.type != ChessType.PAWN }, 0)
            return when (!piece.side) {
                ChessSide.WHITE -> Loc(4 * 8 - 1 - 2 * pos.first, 101, 8 - 3 - 2 * pos.second)
                ChessSide.BLACK -> Loc(8 + 2 * pos.first, 101, 8 * 4 + 2 + 2 * pos.second)
            }
        }

        fun fillFloor(pos: ChessPosition, floor: Material) {
            val (x,y,z) = getPieceLoc(pos)
            (-1..1).star(-1..1) { i, j ->
                board.game.world.getBlockAt(x + i, y - 1, z + j).type = floor
            }
        }

        fun clearPiece(pos: ChessPosition) {
            board.game.world.getBlockAt(getPieceLoc(pos)).type = Material.AIR
        }
    }

    val renderer = Renderer(this)

    private val boardState = (0..7).star(0..7) { i, j ->
        val pos = ChessPosition(i, j)
        Pair(pos, ChessSquare(pos, this))
    }.toMap()

    private var movesSinceLastCapture = 0
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

    var lastMove: ChessMove? = null

    override fun start() {
        render()
        setFromFEN(settings.genFEN())
    }

    override fun update() {}
    override fun stop() {}
    override fun spectatorJoin(p: Player) {}
    override fun spectatorLeave(p: Player) {}
    override fun startTurn() {
        updateMoves()
        checkForGameEnd()
    }

    override fun endTurn() {}

    fun render() {
        for (i in 0 until 8 * 5) {
            for (j in 0 until 8 * 5) {
                game.world.getBlockAt(i, 100, j).type = Material.DARK_OAK_PLANKS
                if (i in 8 - 1..8 * 4 && j in 8 - 1..8 * 4) {
                    game.world.getBlockAt(i, 101, j).type = Material.DARK_OAK_PLANKS
                }
            }
        }
        boardState.values.forEach { it.render() }
    }

    override fun clear() {
        boardState.values.forEach { it.clear() }
        for (i in 0 until 8 * 5) {
            for (j in 0 until 8 * 5) {
                game.world.getBlockAt(i, 100, j).type = Material.AIR
                game.world.getBlockAt(i, 101, j).type = Material.AIR
            }
        }
    }

    fun piecesOf(side: ChessSide) = pieces.filter { it.side == side }

    operator fun plusAssign(captured: ChessPiece.Captured) {
        captured.render(game.world.getBlockAt(renderer.getCapturedLoc(captured)))
        capturedPieces += captured
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
        capturedPieces.forEach { it.hide(game.world.getBlockAt(renderer.getCapturedLoc(it))) }
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
                        ChessType.parseFromChar(it),
                        if (it.isUpperCase()) ChessSide.WHITE else ChessSide.BLACK,
                        getSquare(ChessPosition(x,y))!!,
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

        if (parts[3] != "-") {
            val pos = ChessPosition.parseFromString(parts[2])
            val piece = this[pos.plusR(1)] ?: this[pos.plusR(-1)]!!
            val origin = getSquare(piece.pos.plusR(-2 * piece.side.direction))!!
            val target = piece.square
            piece.move(origin)
            lastMove = ChessMove.Normal(piece,target)
            piece.move(target)
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
                val piece = this@Chessboard[ChessPosition(x, y)]
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
            if (it != null && it is ChessMove.Normal && it.target.piece?.type == ChessType.PAWN
                && abs(it.origin.pos.rank - it.target.pos.rank) == 2
            ) {
                append(it.origin.pos.copy(rank = (it.origin.pos.rank + it.target.pos.rank) / 2))
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
                val piece = this@Chessboard[ChessPosition(x, y)]
                if (piece == null) {
                    append(" ")
                } else {
                    val ch = piece.type.character
                    append(if (piece.side == ChessSide.WHITE) ch.toUpperCase() else ch)
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
            if (it != null && it is ChessMove.Normal && it.target.piece?.type == ChessType.PAWN
                && abs(it.origin.pos.rank - it.target.pos.rank) == 2
            ) {
                append(it.origin.pos.copy(rank = (it.origin.pos.rank + it.target.pos.rank) / 2))
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

    fun resetMovesSinceLastCapture() {
        movesSinceLastCapture = 0
    }
}
