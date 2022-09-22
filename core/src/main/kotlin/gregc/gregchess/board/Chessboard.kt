package gregc.gregchess.board

import gregc.gregchess.*
import gregc.gregchess.component.*
import gregc.gregchess.event.*
import gregc.gregchess.match.ChessMatch
import gregc.gregchess.move.Move
import gregc.gregchess.move.connector.*
import gregc.gregchess.piece.*
import gregc.gregchess.results.EndReason
import gregc.gregchess.results.drawBy
import gregc.gregchess.variant.ChessVariant
import kotlinx.serialization.*
import kotlin.collections.component1
import kotlin.collections.component2

class SetFenEvent(val FEN: FEN) : ChessEvent

@Serializable
private class Square(
    var piece: BoardPiece? = null,
    val flags: MutableMap<ChessFlag, MutableList<Int>> = mutableMapOf()
) {
    @Transient var bakedMoves: List<Move>? = null
    @Transient var bakedLegalMoves: List<Move>? = null

    override fun toString() = "Square(piece=$piece, flags=$flags)"

    fun empty(board: ChessboardFacade) {
        piece?.let(board::callClearEvent)
        bakedMoves = null
        bakedLegalMoves = null
        flags.clear()
    }

    fun copy(): Square = Square(piece, flags.mapValues { it.value.toMutableList() }.toMutableMap()).also { it.bakedMoves = bakedMoves; it.bakedLegalMoves = bakedLegalMoves }
}

@Serializable
private class BoardCounters(var halfmoveClock: Int, var fullmoveCounter: Int, var currentColor: Color)

private class SquareChessboard(private val squares: Map<Pos, Square>, private val capturedPieces_: MutableList<CapturedPiece>, private val counters: BoardCounters) : ChessboardConnector {

    override var halfmoveClock: Int by counters::halfmoveClock

    override val captured: PieceHolder<CapturedPiece> = object : PieceHolder<CapturedPiece> {
        override val pieces: List<CapturedPiece> get() = capturedPieces_.toList()
        override fun canExist(p: CapturedPiece): Boolean = true
        override fun create(p: CapturedPiece) {
            capturedPieces_.add(p)
        }
        override fun exists(p: CapturedPiece): Boolean = p in capturedPieces_
        override fun destroy(p: CapturedPiece) {
            capturedPieces_.removeAt(capturedPieces_.indexOfLast { it == p })
        }
    }

    override val pieces get() = squares.values.mapNotNull { it.piece }

    override operator fun get(pos: Pos) = squares[pos]?.piece

    override fun getFlags(pos: Pos): Map<ChessFlag, Int> =
        squares[pos]?.flags?.filterValues { it.isNotEmpty() }?.mapValues { it.value.last() }.orEmpty()

    override fun get(pos: Pos, flag: ChessFlag): Int? = squares[pos]?.flags?.get(flag)?.last()

    override fun getMoves(pos: Pos) = squares[pos]?.bakedMoves.orEmpty()
    override fun getLegalMoves(pos: Pos) = squares[pos]?.bakedLegalMoves.orEmpty()

    override fun create(p: BoardPiece) {
        checkCanExist(p)
        squares[p.pos]?.piece = p
    }

    override fun destroy(p: BoardPiece) {
        checkExists(p)
        squares[p.pos]?.piece = null
    }

    override fun set(pos: Pos, flag: ChessFlag, age: Int) {
        squares[pos]?.flags?.let {
            it[flag] = it[flag] ?: mutableListOf()
            it[flag]!! += age
        }
    }
}

@Serializable
class Chessboard private constructor (
    val initialFEN: FEN, // TODO: don't rely on FEN
    private val squares: Map<Pos, Square>, // TODO: add other board shapes and sizes
    private val counters: BoardCounters = BoardCounters(initialFEN.halfmoveClock, initialFEN.fullmoveCounter, initialFEN.currentColor),
    private val boardHashes: MutableMap<Int, Int> = mutableMapOf(initialFEN.hashed() to 1),
    private val capturedPieces: MutableList<CapturedPiece> = mutableListOf(),
    @SerialName("moveHistory") private val moveHistory_: MutableList<Move> = mutableListOf()
) : Component, ChessboardConnector by SquareChessboard(squares, capturedPieces, counters) {
    private constructor(variant: ChessVariant, fen: FEN) : this(fen, fen.toSquares(variant))
    constructor(variant: ChessVariant, variantOptions: Long, fen: FEN? = null) :
            this(variant, (fen ?: variant.genFEN(variantOptions)).also { variant.validateFEN(it, variantOptions) })

    override val type get() = ComponentType.CHESSBOARD

    var fullmoveCounter
        get() = counters.fullmoveCounter
        set(v) { counters.fullmoveCounter = v }
    var currentColor: Color
        get() = counters.currentColor
        set(v) { counters.currentColor = v }
    val moveHistory get() = moveHistory_.toList()

    private val boardState
        get() = FEN.boardStateFromPieces(squares.mapNotNull { (p, s) -> s.piece?.let { Pair(p, it.piece) } }.toMap())

    internal operator fun plusAssign(piece: BoardPiece) {
        squares[piece.pos]?.piece = piece
    }

    val lastNormalMove
        get() = moveHistory_.lastOrNull { !it.isPhantomMove }

    var lastMove
        get() = moveHistory_.lastOrNull()
        set(v) {
            if (v != null)
                moveHistory_ += v
            else
                moveHistory_.clear()
        }

    override fun init(match: ChessMatch, events: EventListenerRegistry) {
        events.register<TurnEvent>(OrderConstraint(runBeforeAll = true)) { handleTurnEvent(match, it) }
        events.register<ChessBaseEvent> { handleBaseEvent(match, it) }
        events.register<AddMoveConnectorsEvent> { e ->
            e[MoveConnectorType.CHESSBOARD] = getFacade(match)
        }
        events.register<AddFakeMoveConnectorsEvent> { e ->
            e[MoveConnectorType.CHESSBOARD] = FakeChessboardConnector(
                squares.mapValues { it.value.copy() }, capturedPieces.toMutableList(),
                BoardCounters(halfmoveClock, fullmoveCounter, currentColor),
                match.variant, match.variantOptions
            )
        }
    }

    private fun handleTurnEvent(match: ChessMatch, e: TurnEvent) {
        if (e == TurnEvent.END) {
            if (currentColor == Color.BLACK) {
                fullmoveCounter++
            }
            for (s in squares.values)
                for (f in s.flags)
                    f.value.replaceAll { it + 1 }
            addBoardHash(getFEN().copy(currentColor = !currentColor))
        }
        if (e == TurnEvent.UNDO) {
            for (s in squares.values) {
                for (f in s.flags) {
                    f.value.replaceAll { it - 1 }
                    f.value.removeIf { it == 0 }
                }
                s.flags.entries.removeIf { it.flagAges.isEmpty() }
            }
        }
        if (e.ending)
            updateMoves(match.variant, match.variantOptions)
    }

    private fun handleBaseEvent(match: ChessMatch, e: ChessBaseEvent) {
        if (e == ChessBaseEvent.SYNC || e == ChessBaseEvent.START) {
            val facade = getFacade(match)
            updateMoves(match.variant, match.variantOptions)
            pieces.forEach(facade::callSpawnedEvent)
            capturedPieces.forEach(facade::callSpawnedEvent)
        }
    }

    operator fun plusAssign(captured: CapturedPiece) {
        capturedPieces += captured
    }

    operator fun minusAssign(captured: CapturedPiece) {
        capturedPieces.removeAt(capturedPieces.lastIndexOf(captured))
    }

    fun setFromFEN(match: ChessMatch, fen: FEN) {
        val facade = getFacade(match)
        match.variant.validateFEN(fen, match.variantOptions)
        capturedPieces.asReversed().forEach(facade::callClearEvent)
        squares.values.forEach { it.empty(facade) }
        fen.forEachSquare(match.variant) { p -> this += p }

        halfmoveClock = fen.halfmoveClock

        fullmoveCounter = fen.fullmoveCounter

        currentColor = fen.currentColor

        if (fen.enPassantSquare != null) {
            set(fen.enPassantSquare, ChessFlag.EN_PASSANT, 1)
        }

        updateMoves(match.variant, match.variantOptions)

        boardHashes.clear()
        addBoardHash(fen)
        pieces.forEach(facade::callSpawnedEvent)
        match.callEvent(SetFenEvent(fen))
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
            currentColor,
            byColor(::castling),
            squares.entries.firstOrNull { (_, s) -> s.flags.any { it.key == ChessFlag.EN_PASSANT && it.flagActive } }?.key,
            halfmoveClock,
            fullmoveCounter
        )
    }

    fun checkForRepetition(match: ChessMatch) {
        if ((boardHashes[getFEN().copy(currentColor = !currentColor).hashed()] ?: 0) >= 3)
            match.stop(drawBy(EndReason.REPETITION))
    }

    fun checkForFiftyMoveRule(match: ChessMatch) {
        if (halfmoveClock >= 100)
            match.stop(drawBy(EndReason.FIFTY_MOVES))
    }

    private fun addBoardHash(fen: FEN): Int {
        val hash = fen.hashed()
        boardHashes[hash] = (boardHashes[hash] ?: 0) + 1
        return boardHashes[hash]!!
    }

    fun undoLastMove(match: ChessMatch) {
        lastMove?.let {
            if (!it.isPhantomMove) {
                val hash = getFEN().hashed()
                boardHashes[hash] = (boardHashes[hash] ?: 1) - 1
                if (boardHashes[hash] == 0) boardHashes -= hash
                match.undoMove(it)
                if (currentColor == Color.WHITE)
                    fullmoveCounter--
                moveHistory_.removeLast()
                match.previousTurn()
            } else {
                match.undoMove(it)
                moveHistory_.removeLast()
            }
        }
    }

    private class FakeChessboardConnector(
        val squares: Map<Pos, Square>, val capturedPieces: MutableList<CapturedPiece>, val counters: BoardCounters,
        val variant: ChessVariant, val variantOptions: Long
    ) : ChessboardConnector by SquareChessboard(squares, capturedPieces, counters), ChessboardFacadeConnector {
        override fun callEvent(event: ChessEvent) { }
        override fun updateMoves() = updateMoves(squares, variant, variantOptions)
    }

    fun getFacade(match: ChessMatch) = match.componentsFacade.makeCachedFacade(::ChessboardFacade, this)

    fun updateMoves(variant: ChessVariant, variantOptions: Long) = updateMoves(squares, variant, variantOptions)

    companion object {

        private fun FEN.toSquares(variant: ChessVariant): Map<Pos, Square> {
            val pieces = toPieces(variant)
            return buildMap {
                for (i in 0 until 8)
                    for (j in 0 until 8) {
                        val pos = Pos(i, j)
                        val piece = pieces[pos]
                        val flags = if (pos == enPassantSquare) mutableMapOf(ChessFlag.EN_PASSANT to mutableListOf(1)) else mutableMapOf()
                        put(pos, Square(piece, flags))
                    }
            }
        }

        fun createFakeConnector(variant: ChessVariant, variantOptions: Long, fen: FEN = variant.genFEN(variantOptions)): ChessboardFacadeConnector =
            FakeChessboardConnector(fen.toSquares(variant), mutableListOf(), BoardCounters(fen.halfmoveClock, fen.fullmoveCounter, fen.currentColor), variant, variantOptions)

        private fun ChessboardView.updateMoves(squares: Map<Pos, Square>, variant: ChessVariant, variantOptions: Long) {
            for ((_, square) in squares) {
                square.bakedMoves = square.piece?.let { p -> variant.getPieceMoves(p, this, variantOptions) }
            }
            for ((_, square) in squares) {
                square.bakedLegalMoves = square.bakedMoves?.filter { variant.isLegal(it, this) }
            }
        }
    }
}

class ChessboardFacade(match: ChessMatch, component: Chessboard) : ComponentFacade<Chessboard>(match, component), ChessboardConnector by component, ChessboardFacadeConnector {
    val initialFEN get() = component.initialFEN
    var currentColor by component::currentColor
        internal set
    var fullmoveCounter by component::fullmoveCounter
    val moveHistory get() = component.moveHistory
    val lastNormalMove get() = component.lastNormalMove
    var lastMove by component::lastMove
    operator fun plusAssign(captured: CapturedPiece) = component.plusAssign(captured)
    operator fun minusAssign(captured: CapturedPiece) = component.minusAssign(captured)
    fun setFromFEN(fen: FEN) = component.setFromFEN(match, fen)
    fun getFEN() = component.getFEN()
    fun checkForRepetition() = component.checkForRepetition(match)
    fun checkForFiftyMoveRule() = component.checkForFiftyMoveRule(match)
    fun undoLastMove() = component.undoLastMove(match)
    override fun updateMoves() = component.updateMoves(match.variant, match.variantOptions)

    internal fun callSpawnedEvent(p: CapturedPiece) = callEvent(captured.createSpawnedEvent(p))
    internal fun callSpawnEvent(p: CapturedPiece) = callEvent(captured.createSpawnEvent(p))
    internal fun callClearEvent(p: CapturedPiece) = callEvent(captured.createClearEvent(p))
}