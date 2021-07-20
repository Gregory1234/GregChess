package gregc.gregchess.chess.component

import gregc.gregchess.chess.*
import gregc.gregchess.glog
import gregc.gregchess.rangeTo
import java.util.*
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.math.abs

class Chessboard(private val game: ChessGame, private val settings: Settings) : Component {
    data class Settings(val initialFEN: FEN?, internal val chess960: Boolean = initialFEN?.chess960 ?: false) :
        Component.Settings<Chessboard> {
        override fun getComponent(game: ChessGame) = Chessboard(game, this)

        fun genFEN(game: ChessGame) = initialFEN ?: game.variant.genFEN(chess960)

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

    private val squares = (Pair(0, 0)..Pair(7, 7)).map { (i, j) -> Pos(i, j) }.associateWith { Square(it, game) }

    private val boardState
        get() = FEN.BoardState.fromPieces(squares.mapNotNull { (p, s) -> s.piece?.info?.let { Pair(p, it) } }.toMap())

    private var movesSinceLastCapture = 0u
    var fullMoveCounter = 0u
        private set
    var halfMoveCounter = 0u
        private set
    private val boardHashes = mutableMapOf<Int, Int>()

    private val capturedPieces = mutableListOf<CapturedPiece>()

    val pieces: List<BoardPiece>
        get() = squares.values.mapNotNull { it.piece }

    operator fun plusAssign(piece: BoardPiece) {
        piece.square.piece = piece
    }

    operator fun get(pos: Pos) = squares[pos]

    private val moves: MutableList<MoveData> = mutableListOf()

    val moveHistory: List<MoveData>
        get() = moves

    val initialFEN = settings.genFEN(game)

    val chess960: Boolean
        get() {
            if (settings.chess960)
                return true
            val whiteKing = kingOf(Side.WHITE)
            val blackKing = kingOf(Side.BLACK)
            val whiteRooks = piecesOf(Side.WHITE, PieceType.ROOK).filter { !it.hasMoved }
            val blackRooks = piecesOf(Side.BLACK, PieceType.ROOK).filter { !it.hasMoved }
            if (whiteKing != null && !whiteKing.hasMoved && whiteKing.pos != Pos(4, 0))
                return true
            if (blackKing != null && !blackKing.hasMoved && blackKing.pos != Pos(4, 7))
                return true
            if (whiteRooks.any { it.pos.rank == 0 && it.pos.file !in listOf(0, 7) })
                return true
            if (blackRooks.any { it.pos.rank == 7 && it.pos.file !in listOf(0, 7) })
                return true
            return false
        }

    var lastMove
        get() = moves.lastOrNull()
        set(v) {
            if (v != null)
                moves += v
            else
                moves.clear()
        }

    @GameEvent(GameBaseEvent.PRE_PREVIOUS_TURN, mod = TimeModifier.EARLY)
    fun previousTurn() {
        updateMoves()
    }

    @GameEvent(GameBaseEvent.END_TURN)
    fun endTurn() {
        updateMoves()
        if (game.currentTurn == Side.BLACK) {
            val wLast = (if (moves.size <= 1) null else moves[moves.size - 2])
            val bLast = lastMove
            game.players.forEachReal { p ->
                p.sendLastMoves(fullMoveCounter, wLast, bLast)
            }
            fullMoveCounter++
        }
        addBoardHash(getFEN().copy(currentTurn = !game.currentTurn))
    }

    @GameEvent(GameBaseEvent.STOP)
    fun stop() {
        if (game.currentTurn == Side.WHITE) {
            val wLast = lastMove
            game.players.forEachReal { p ->
                p.sendLastMoves(fullMoveCounter, wLast, null)
            }
        }
    }

    private fun render() {
        game.renderers.forEach { it.renderBoardBase() }
        squares.values.forEach { it.render() }
        glog.mid("Rendered chessboard", game.uniqueId)
    }

    @GameEvent(GameBaseEvent.CLEAR, GameBaseEvent.PANIC)
    fun clear() {
        game.renderers.forEach { it.removeBoard() }
        glog.mid("Cleared chessboard", game.uniqueId)
    }

    operator fun contains(pieceUniqueId: UUID) = pieces.any { it.uniqueId == pieceUniqueId }
    operator fun get(pieceUniqueId: UUID) = pieces.firstOrNull { it.uniqueId == pieceUniqueId }

    fun piecesOf(side: Side) = pieces.filter { it.side == side }
    fun piecesOf(side: Side, type: PieceType) = pieces.filter { it.side == side && it.type == type }

    fun kingOf(side: Side) = piecesOf(side).firstOrNull { it.type == PieceType.KING }

    operator fun plusAssign(captured: CapturedPiece) {
        captured.render()
        capturedPieces += captured
        glog.low("Added captured", game.uniqueId, captured)
    }

    operator fun minusAssign(captured: CapturedPiece) {
        capturedPieces -= captured
        captured.hide()
        glog.low("Removed captured", game.uniqueId, captured)
    }

    fun getMoves(pos: Pos) = squares[pos]?.bakedMoves.orEmpty()

    fun updateMoves() {
        squares.forEach { (_, square) ->
            square.bakedMoves = square.piece?.let { p -> game.variant.getPieceMoves(p) }
        }
        squares.forEach { (_, square) ->
            square.bakedLegalMoves = square.bakedMoves?.filter { game.variant.isLegal(it) }
        }
    }

    @GameEvent(GameBaseEvent.START, mod = TimeModifier.EARLY)
    fun start() = setFromFEN(settings.genFEN(game))

    @GameEvent(GameBaseEvent.STOP)
    fun sendPGN() {
        val pgn = PGN.generate(game)
        game.players.forEachReal { it.sendPGN(pgn) }
    }

    fun setFromFEN(fen: FEN) {
        clear()
        squares.values.forEach(Square::empty)
        render()
        fen.forEachSquare { (pos, p, hm) ->
            this += BoardPiece(p, this[pos]!!, hm)
        }
        if (fen.enPassantSquare != null) {
            val pos = fen.enPassantSquare
            val target = this[pos.plusR(1)] ?: this[pos.plusR(-1)]!!
            val piece = target.piece!!
            val origin = this[piece.pos.plusR(-2 * piece.side.direction)]!!
            lastMove = MoveData(piece.piece, origin, target, "", true) {}
        }

        movesSinceLastCapture = fen.halfmoveClock

        fullMoveCounter = fen.fullmoveClock

        if (fen.currentTurn != game.currentTurn)
            game.nextTurn()
        else
            updateMoves()

        boardHashes.clear()
        addBoardHash(fen)
        game.variant.chessboardSetup(this)
    }

    fun getFEN(): FEN {
        fun castling(side: Side) =
            if (kingOf(side)?.hasMoved == false)
                piecesOf(side, PieceType.ROOK)
                    .filter { !it.hasMoved && it.pos.rank == kingOf(side)?.pos?.rank }
                    .map { it.pos.file }
            else emptyList()

        return FEN(
            boardState,
            game.currentTurn,
            BySides(::castling),
            lastMove?.takeIf { it.piece.type == PieceType.PAWN && abs(it.origin.pos.rank - it.target.pos.rank) == 2 }
                ?.let { it.origin.pos.copy(rank = (it.origin.pos.rank + it.target.pos.rank) / 2) },
            movesSinceLastCapture,
            fullMoveCounter
        )
    }

    fun checkForRepetition() {
        if (boardHashes[getFEN().copy(currentTurn = !game.currentTurn).hashed()] ?: 0 >= 3)
            game.stop(EndReason.Repetition())
    }

    fun checkForFiftyMoveRule() {
        if (movesSinceLastCapture >= 100u)
            game.stop(EndReason.FiftyMoves())
    }

    private fun addBoardHash(fen: FEN): Int {
        val hash = fen.hashed()
        boardHashes[hash] = (boardHashes[hash] ?: 0) + 1
        return boardHashes[hash]!!
    }

    fun resetMovesSinceLastCapture(): () -> Unit {
        val m = movesSinceLastCapture
        halfMoveCounter++
        movesSinceLastCapture = 0u
        return {
            movesSinceLastCapture = m
            halfMoveCounter--
        }
    }

    fun increaseMovesSinceLastCapture(): () -> Unit {
        movesSinceLastCapture++
        halfMoveCounter++
        return {
            movesSinceLastCapture--
            halfMoveCounter--
        }
    }

    fun undoLastMove() {
        lastMove?.let {
            it.clear()
            val hash = getFEN().hashed()
            boardHashes[hash] = (boardHashes[hash] ?: 1) - 1
            game.variant.undoLastMove(it)
            if (game.currentTurn == Side.WHITE)
                fullMoveCounter--
            moves.removeLast()
            lastMove?.render()
            game.previousTurn()
            glog.mid("Undid last move", game.uniqueId, it)
        }
    }

    fun nextCapturedPos(type: PieceType, by: Side): CapturedPos {
        val cap = capturedPieces.filter { it.pos.by == by }
        return CapturedPos(by,
            if (type == PieceType.PAWN) Pair(cap.count { it.type == PieceType.PAWN }, 1)
            else Pair(cap.count { it.type != PieceType.PAWN }, 0)
        )
    }
}
