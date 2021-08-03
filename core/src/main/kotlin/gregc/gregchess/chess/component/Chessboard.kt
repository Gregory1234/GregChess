package gregc.gregchess.chess.component

import gregc.gregchess.chess.*
import gregc.gregchess.rangeTo
import java.util.*
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

class Chessboard(private val game: ChessGame, private val settings: Settings) : Component {
    data class SetFenEvent(val FEN: FEN) : ChessEvent

    class Settings(private val initialFEN: FEN?, internal val chess960: Boolean = initialFEN?.chess960 ?: false) :
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
                        println("Chessboard configuration ${name.drop(4)} is in a wrong format, defaulted to normal!")
                        normal
                    } else {
                        println("Invalid chessboard configuration $name, defaulted to normal!")
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

    operator fun plusAssign(piece: PieceInfo) {
        this += BoardPiece(piece.piece, this[piece.pos]!!, piece.hasMoved)
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
            val whiteKing = kingOf(white)
            val blackKing = kingOf(black)
            val whiteRooks = piecesOf(white, PieceType.ROOK).filter { !it.hasMoved }
            val blackRooks = piecesOf(black, PieceType.ROOK).filter { !it.hasMoved }
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

    @ChessEventHandler
    fun endTurn(e: TurnEvent) {
        if (e == TurnEvent.END) {
            if (game.currentTurn == black) {
                val wLast = (if (moves.size <= 1) null else moves[moves.size - 2])
                val bLast = lastMove
                game.players.forEachReal { p ->
                    p.sendLastMoves(fullMoveCounter, wLast, bLast)
                }
                fullMoveCounter++
            }
            addBoardHash(getFEN().copy(currentTurn = !game.currentTurn))
            for (s in squares.values)
                for (f in s.flags)
                    f.timeLeft--
        }
        if (e == TurnEvent.UNDO) {
            for (s in squares.values) {
                for (f in s.flags) {
                    f.timeLeft++
                }
                s.flags.removeIf { f ->
                    f.type.startTime.toInt() <= f.timeLeft
                }
            }
        }
        if (e.ending)
            updateMoves()
    }

    @ChessEventHandler
    fun handleEvents(e: GameBaseEvent) = when (e) {
        GameBaseEvent.START -> setFromFEN(settings.genFEN(game))
        GameBaseEvent.STOP -> {
            stop()
            sendPGN()
        }
        else -> {
        }
    }

    private fun stop() {
        if (game.currentTurn == white) {
            val wLast = lastMove
            game.players.forEachReal { p ->
                p.sendLastMoves(fullMoveCounter, wLast, null)
            }
        }
    }

    operator fun contains(pieceUniqueId: UUID) = pieces.any { it.uuid == pieceUniqueId }
    operator fun get(pieceUniqueId: UUID) = pieces.firstOrNull { it.uuid == pieceUniqueId }

    fun piecesOf(side: Side) = pieces.filter { it.side == side }
    fun piecesOf(side: Side, type: PieceType) = pieces.filter { it.side == side && it.type == type }

    fun kingOf(side: Side) = piecesOf(side).firstOrNull { it.type == PieceType.KING }

    operator fun plusAssign(captured: CapturedPiece) {
        capturedPieces += captured
    }

    operator fun minusAssign(captured: CapturedPiece) {
        capturedPieces -= captured
    }

    fun getMoves(pos: Pos) = squares[pos]?.bakedMoves.orEmpty()

    fun updateMoves() {
        for ((_, square) in squares) {
            square.bakedMoves = square.piece?.let { p -> game.variant.getPieceMoves(p) }
        }
        for ((_, square) in squares) {
            square.bakedLegalMoves = square.bakedMoves?.filter { game.variant.isLegal(it) }
        }
    }

    private fun sendPGN() {
        val pgn = PGN.generate(game)
        game.players.forEachReal { it.sendPGN(pgn) }
    }

    fun setFromFEN(fen: FEN) {
        squares.values.forEach(Square::empty)
        fen.forEachSquare(game.variant.pieceTypes) { (pos, p, hm) ->
            this += BoardPiece(p, this[pos]!!, hm)
        }

        movesSinceLastCapture = fen.halfmoveClock

        fullMoveCounter = fen.fullmoveClock

        if (fen.currentTurn != game.currentTurn)
            game.nextTurn()


        if (fen.enPassantSquare != null) {
            this[fen.enPassantSquare]?.flags?.plusAssign(ChessFlag(PawnMovement.EN_PASSANT, 0))
        }

        updateMoves()

        boardHashes.clear()
        addBoardHash(fen)
        game.variant.chessboardSetup(this)
        game.callEvent(SetFenEvent(fen))
        squares.values.forEach(Square::update)
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
            bySides(::castling),
            squares.values.firstOrNull { s -> s.flags.any { it.type == PawnMovement.EN_PASSANT && it.timeLeft >= 0 } }?.pos,
            movesSinceLastCapture,
            fullMoveCounter
        )
    }

    fun checkForRepetition() {
        if ((boardHashes[getFEN().copy(currentTurn = !game.currentTurn).hashed()] ?: 0) >= 3)
            game.stop(drawBy(EndReason.REPETITION))
    }

    fun checkForFiftyMoveRule() {
        if (movesSinceLastCapture >= 100u)
            game.stop(drawBy(EndReason.FIFTY_MOVES))
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
            if (game.currentTurn == white)
                fullMoveCounter--
            moves.removeLast()
            lastMove?.render()
            game.previousTurn()
        }
    }

    fun nextCapturedPos(type: PieceType, by: Side): CapturedPos {
        val cap = capturedPieces.filter { it.pos.by == by }
        return CapturedPos(
            by,
            if (type == PieceType.PAWN) Pair(cap.count { it.type == PieceType.PAWN }, 1)
            else Pair(cap.count { it.type != PieceType.PAWN }, 0)
        )
    }
}
