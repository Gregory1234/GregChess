package gregc.gregchess.chess.component

import gregc.gregchess.chess.*
import gregc.gregchess.chess.move.Move
import gregc.gregchess.chess.piece.*
import gregc.gregchess.chess.variant.ChessVariant
import gregc.gregchess.rangeTo
import kotlinx.serialization.Serializable
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.reflect.KClass

class SetFenEvent(val FEN: FEN) : ChessEvent

private class Square(val pos: Pos, val game: ChessGame) {
    var piece: BoardPiece? = null
    val flags = mutableMapOf<ChessFlag, MutableList<UInt>>()

    var bakedMoves: List<Move>? = null
    var bakedLegalMoves: List<Move>? = null

    val board
        get() = game.board

    override fun toString() = "Square(game.uuid=${game.uuid}, pos=$pos, piece=$piece, flags=$flags)"

    fun empty() {
        piece?.clear(board)
        bakedMoves = null
        bakedLegalMoves = null
        flags.clear()
    }
}

@Serializable
data class ChessboardState internal constructor (
    val initialFEN: FEN,
    val pieces: Map<Pos, BoardPiece>,
    val halfmoveClock: UInt = initialFEN.halfmoveClock,
    val fullmoveCounter: UInt = initialFEN.fullmoveCounter,
    val boardHashes: Map<Int, Int> = mapOf(initialFEN.hashed() to 1),
    val capturedPieces: List<CapturedPiece> = emptyList(),
    val flags: Map<Pos, Map<ChessFlag, List<UInt>>> = enPassantFlag(initialFEN.enPassantSquare),
    val moveHistory: List<Move> = emptyList()
) : ComponentData<Chessboard> {
    private constructor(variant: ChessVariant, fen: FEN) : this(fen, fen.toPieces(variant))
    constructor(variant: ChessVariant, fen: FEN? = null, chess960: Boolean = false) :
            this(variant, fen ?: variant.genFEN(chess960))

    override val componentClass: KClass<out Chessboard> get() = Chessboard::class

    override fun getComponent(game: ChessGame) = Chessboard(game, this)

    companion object {

        private fun enPassantFlag(square: Pos?) =
            square?.let { mapOf(it to mapOf(ChessFlag.EN_PASSANT to listOf(1u))) }.orEmpty()

    }
}

class Chessboard(game: ChessGame, initialState: ChessboardState) : Component(game) {


    private val squares = (Pair(0, 0)..Pair(7, 7)).map { (i, j) -> Pos(i, j) }.associateWith { p ->
        Square(p, game).also { s ->
            val piece = initialState.pieces[p]
            s.piece = piece
            s.flags += initialState.flags[p].orEmpty().mapValues { it.value.toMutableList() }
        }
    }

    private val boardState
        get() = FEN.boardStateFromPieces(squares.mapNotNull { (p, s) -> s.piece?.let { Pair(p, it.piece) } }.toMap())

    val initialFEN = initialState.initialFEN
    var fullmoveCounter = initialState.fullmoveCounter
        private set
    var halfmoveClock = initialState.halfmoveClock
    private val boardHashes = initialState.boardHashes.toMutableMap()
    private val capturedPieces = initialState.capturedPieces.toMutableList()

    override val data
        get() = ChessboardState(
            initialFEN, piecesByPos, halfmoveClock, fullmoveCounter, boardHashes, capturedPieces, posFlags, moveHistory
        )

    val pieces: List<BoardPiece> get() = squares.values.mapNotNull { it.piece }

    private val piecesByPos get() = squares.mapNotNull { it.value.piece }.associateBy { it.pos }

    private val posFlags get() = squares.values.filter { it.flags.isNotEmpty() }.associate { it.pos to it.flags }

    operator fun plusAssign(piece: BoardPiece) {
        squares[piece.pos]?.piece = piece
    }

    operator fun get(pos: Pos) = squares[pos]?.piece

    fun clearPiece(pos: Pos) {
        squares[pos]?.piece = null
    }

    fun addFlag(pos: Pos, flag: ChessFlag, age: UInt = 0u) {
        squares[pos]?.flags?.let {
            it[flag] = it[flag] ?: mutableListOf()
            it[flag]!! += age
        }
    }

    fun getFlags(pos: Pos): Map<ChessFlag, UInt> =
        squares[pos]?.flags?.filterValues { it.isNotEmpty() }?.mapValues { it.value.last() }.orEmpty()

    fun hasActiveFlag(pos: Pos, flag: ChessFlag): Boolean = getFlags(pos)[flag]?.let(flag.isActive) ?: false

    private val moves: MutableList<Move> = initialState.moveHistory.toMutableList()

    val moveHistory: List<Move> get() = moves

    val simpleCastling: Boolean get() = game.settings.simpleCastling

    val chess960: Boolean
        get() {
            if (initialFEN.chess960)
                return true
            val whiteKing = kingOf(Color.WHITE)
            val blackKing = kingOf(Color.BLACK)
            val whiteRooks = piecesOf(Color.WHITE, PieceType.ROOK).filter { !it.hasMoved }
            val blackRooks = piecesOf(Color.BLACK, PieceType.ROOK).filter { !it.hasMoved }
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
            if (game.currentTurn == Color.BLACK) {
                fullmoveCounter++
            }
            for (s in squares.values)
                for (f in s.flags)
                    f.value.replaceAll { it + 1u }
            addBoardHash(getFEN().copy(currentTurn = !game.currentTurn))
        }
        if (e == TurnEvent.UNDO) {
            for (s in squares.values) {
                for (f in s.flags) {
                    f.value.replaceAll { it + 1u }
                    f.value.removeIf { it == 0u }
                }
                s.flags.entries.removeIf { it.flagAges.isEmpty() }
            }
        }
        if (e.ending)
            updateMoves()
    }

    @ChessEventHandler
    fun handleEvents(e: GameBaseEvent) {
        if (e == GameBaseEvent.SYNC || e == GameBaseEvent.START) {
            updateMoves()
            pieces.forEach { it.sendCreated(this) }
        }
    }

    fun piecesOf(color: Color) = pieces.filter { it.color == color }
    fun piecesOf(color: Color, type: PieceType) = pieces.filter { it.color == color && it.type == type }

    fun kingOf(color: Color) = piecesOf(color).firstOrNull { it.type == PieceType.KING }

    operator fun plusAssign(captured: CapturedPiece) {
        capturedPieces += captured
    }

    operator fun minusAssign(captured: CapturedPiece) {
        capturedPieces -= captured
    }

    fun getMoves(pos: Pos) = squares[pos]?.bakedMoves.orEmpty()
    fun getLegalMoves(pos: Pos) = squares[pos]?.bakedLegalMoves.orEmpty()

    fun updateMoves() {
        for ((_, square) in squares) {
            square.bakedMoves = square.piece?.let { p -> game.variant.getPieceMoves(p, this) }
        }
        for ((_, square) in squares) {
            square.bakedMoves?.forEach { it.setup(game) }
        }
        for ((_, square) in squares) {
            square.bakedLegalMoves = square.bakedMoves?.filter { game.variant.isLegal(it, game) }
        }
    }

    fun setFromFEN(fen: FEN) {
        squares.values.forEach(Square::empty)
        fen.forEachSquare(game.variant) { p -> this += p }

        halfmoveClock = fen.halfmoveClock

        fullmoveCounter = fen.fullmoveCounter

        if (fen.currentTurn != game.currentTurn)
            game.nextTurn()


        if (fen.enPassantSquare != null) {
            addFlag(fen.enPassantSquare, ChessFlag.EN_PASSANT, 1u)
        }

        updateMoves()

        boardHashes.clear()
        addBoardHash(fen)
        pieces.forEach { it.sendCreated(this) }
        game.callEvent(SetFenEvent(fen))
    }

    fun getFEN(): FEN {
        fun castling(color: Color) =
            if (kingOf(color)?.hasMoved == false)
                piecesOf(color, PieceType.ROOK)
                    .filter { !it.hasMoved && it.pos.rank == kingOf(color)?.pos?.rank }
                    .map { it.pos.file }
            else emptyList()

        return FEN(
            boardState,
            game.currentTurn,
            byColor(::castling),
            squares.values.firstOrNull { s -> s.flags.any { it.key == ChessFlag.EN_PASSANT && it.flagActive } }?.pos,
            halfmoveClock,
            fullmoveCounter
        )
    }

    fun checkForRepetition() {
        if ((boardHashes[getFEN().copy(currentTurn = !game.currentTurn).hashed()] ?: 0) >= 3)
            game.stop(drawBy(EndReason.REPETITION))
    }

    fun checkForFiftyMoveRule() {
        if (halfmoveClock >= 100u)
            game.stop(drawBy(EndReason.FIFTY_MOVES))
    }

    private fun addBoardHash(fen: FEN): Int {
        val hash = fen.hashed()
        boardHashes[hash] = (boardHashes[hash] ?: 0) + 1
        return boardHashes[hash]!!
    }

    fun undoLastMove() {
        lastMove?.let {
            val hash = getFEN().hashed()
            boardHashes[hash] = (boardHashes[hash] ?: 1) - 1
            it.undo(game)
            if (game.currentTurn == Color.WHITE)
                fullmoveCounter--
            moves.removeLast()
            game.previousTurn()
        }
    }

    fun callPieceEvent(e: PieceEvent) = game.callEvent(e)
}
