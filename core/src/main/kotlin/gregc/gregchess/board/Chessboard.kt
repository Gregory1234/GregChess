package gregc.gregchess.board

import gregc.gregchess.*
import gregc.gregchess.match.*
import gregc.gregchess.move.Move
import gregc.gregchess.move.connector.*
import gregc.gregchess.piece.*
import gregc.gregchess.results.EndReason
import gregc.gregchess.results.drawBy
import gregc.gregchess.variant.ChessVariant
import kotlinx.serialization.*
import kotlin.collections.component1
import kotlin.collections.component2

class SetFenEvent(val FEN: FEN) : ChessEvent {
    override val type get() = ChessEventType.SET_FEN
}

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
private class BoardCounters(var halfmoveClock: Int, var fullmoveCounter: Int, var currentTurn: Color)

private class SquareChessboard(override val initialFEN: FEN, private val squares: Map<Pos, Square>, private val capturedPieces_: MutableList<CapturedPiece>, private val counters: BoardCounters) : ChessboardConnector {

    override var halfmoveClock: Int by counters::halfmoveClock
    override val currentTurn: Color get() = counters.currentTurn

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

    override fun updateMoves(variant: ChessVariant, variantOptions: Long) {
        for ((_, square) in squares) {
            square.bakedMoves = square.piece?.let { p -> variant.getPieceMoves(p, this, variantOptions) }
        }
        for ((_, square) in squares) {
            square.bakedLegalMoves = square.bakedMoves?.filter { variant.isLegal(it, this) }
        }
    }
}

@Serializable
class Chessboard private constructor (
    override val initialFEN: FEN,
    private val squares: Map<Pos, Square>,
    private val counters: BoardCounters = BoardCounters(initialFEN.halfmoveClock, initialFEN.fullmoveCounter, initialFEN.currentTurn),
    @SerialName("boardHashes") private val boardHashes_: MutableMap<Int, Int> = mutableMapOf(initialFEN.hashed() to 1),
    @SerialName("capturedPieces") private val capturedPieces_: MutableList<CapturedPiece> = mutableListOf(),
    @SerialName("moveHistory") private val moveHistory_: MutableList<Move> = mutableListOf()
) : Component, ChessboardConnector by SquareChessboard(initialFEN, squares, capturedPieces_, counters) {
    private constructor(variant: ChessVariant, fen: FEN) : this(fen, fen.toSquares(variant))
    constructor(variant: ChessVariant, variantOptions: Long, fen: FEN? = null) :
            this(variant, (fen ?: variant.genFEN(variantOptions)).also { variant.validateFEN(it, variantOptions) })

    override val type get() = ComponentType.CHESSBOARD

    var fullmoveCounter
        get() = counters.fullmoveCounter
        set(v) { counters.fullmoveCounter = v }
    override var currentTurn: Color
        get() = counters.currentTurn
        internal set(v) { counters.currentTurn = v }
    val boardHashes get() = boardHashes_.toMap()
    val capturedPieces get() = capturedPieces_.toList()
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

    override fun init(match: ChessMatch, eventManager: ChessEventManager) {
        eventManager.registerEvent(ChessEventType.TURN) { handleTurnEvent(match, it) }
        eventManager.registerEvent(ChessEventType.BASE) { handleBaseEvent(match, it) }
        eventManager.registerEvent(ChessEventType.ADD_MOVE_CONNECTORS) { e ->
            e[MoveConnectorType.CHESSBOARD] = getFacade(match)
        }
        eventManager.registerEvent(ChessEventType.ADD_FAKE_MOVE_CONNECTORS) { e ->
            e[MoveConnectorType.CHESSBOARD] = FakeChessboardConnector(
                initialFEN,
                squares.mapValues { it.value.copy() }, capturedPieces.toMutableList(),
                BoardCounters(halfmoveClock, fullmoveCounter, currentTurn)
            )
        }
    }

    private fun handleTurnEvent(match: ChessMatch, e: TurnEvent) {
        if (e == TurnEvent.END) {
            if (currentTurn == Color.BLACK) {
                fullmoveCounter++
            }
            for (s in squares.values)
                for (f in s.flags)
                    f.value.replaceAll { it + 1 }
            addBoardHash(getFEN().copy(currentTurn = !currentTurn))
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
        capturedPieces_ += captured
    }

    operator fun minusAssign(captured: CapturedPiece) {
        capturedPieces_.removeAt(capturedPieces_.lastIndexOf(captured))
    }

    fun setFromFEN(match: ChessMatch, fen: FEN) {
        val facade = getFacade(match)
        match.variant.validateFEN(fen, match.variantOptions)
        capturedPieces.asReversed().forEach(facade::callClearEvent)
        squares.values.forEach { it.empty(facade) }
        fen.forEachSquare(match.variant) { p -> this += p }

        halfmoveClock = fen.halfmoveClock

        fullmoveCounter = fen.fullmoveCounter

        currentTurn = fen.currentTurn

        if (fen.enPassantSquare != null) {
            set(fen.enPassantSquare, ChessFlag.EN_PASSANT, 1)
        }

        updateMoves(match.variant, match.variantOptions)

        boardHashes_.clear()
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
            currentTurn,
            byColor(::castling),
            squares.entries.firstOrNull { (_, s) -> s.flags.any { it.key == ChessFlag.EN_PASSANT && it.flagActive } }?.key,
            halfmoveClock,
            fullmoveCounter
        )
    }

    fun checkForRepetition(match: ChessMatch) {
        if ((boardHashes_[getFEN().copy(currentTurn = !currentTurn).hashed()] ?: 0) >= 3)
            match.stop(drawBy(EndReason.REPETITION))
    }

    fun checkForFiftyMoveRule(match: ChessMatch) {
        if (halfmoveClock >= 100)
            match.stop(drawBy(EndReason.FIFTY_MOVES))
    }

    private fun addBoardHash(fen: FEN): Int {
        val hash = fen.hashed()
        boardHashes_[hash] = (boardHashes_[hash] ?: 0) + 1
        return boardHashes_[hash]!!
    }

    fun undoLastMove(match: ChessMatch) {
        lastMove?.let {
            if (!it.isPhantomMove) {
                val hash = getFEN().hashed()
                boardHashes_[hash] = (boardHashes_[hash] ?: 1) - 1
                if (boardHashes_[hash] == 0) boardHashes_ -= hash
                match.undoMove(it)
                if (currentTurn == Color.WHITE)
                    fullmoveCounter--
                moveHistory_.removeLast()
                match.previousTurn()
            } else {
                match.undoMove(it)
                moveHistory_.removeLast()
            }
        }
    }

    private class FakeChessboardConnector(override val initialFEN: FEN, val squares: Map<Pos, Square>, val capturedPieces: MutableList<CapturedPiece>, val counters: BoardCounters)
        : ChessboardConnector by SquareChessboard(initialFEN, squares, capturedPieces, counters), ChessboardFacadeConnector {
        override fun callEvent(event: ChessEvent) { }
    }

    fun getFacade(match: ChessMatch) = match.makeCachedFacade(::ChessboardFacade, this)

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

        private fun createFakeConnector(variant: ChessVariant, fen: FEN): ChessboardConnector =
            FakeChessboardConnector(fen, fen.toSquares(variant), mutableListOf(), BoardCounters(fen.halfmoveClock, fen.fullmoveCounter, fen.currentTurn))

        fun createFakeConnector(variant: ChessVariant, variantOptions: Long, fen: FEN? = null): ChessboardConnector =
            createFakeConnector(variant, fen ?: variant.genFEN(variantOptions))

    }
}

class ChessboardFacade(match: ChessMatch, component: Chessboard) : ComponentFacade<Chessboard>(match, component), ChessboardConnector by component, ChessboardFacadeConnector {
    override var currentTurn
        get() = component.currentTurn
        set(v) { component.currentTurn = v }
    var fullmoveCounter
        get() = component.fullmoveCounter
        set(v) { component.fullmoveCounter = v }
    val boardHashes get() = component.boardHashes
    val capturedPieces get() = component.capturedPieces
    val moveHistory get() = component.moveHistory
    val lastNormalMove get() = component.lastNormalMove
    var lastMove
        get() = component.lastMove
        set(v) { component.lastMove = v }
    operator fun plusAssign(captured: CapturedPiece) = component.plusAssign(captured)
    operator fun minusAssign(captured: CapturedPiece) = component.minusAssign(captured)
    fun setFromFEN(fen: FEN) = component.setFromFEN(match, fen)
    fun getFEN() = component.getFEN()
    fun checkForRepetition() = component.checkForRepetition(match)
    fun checkForFiftyMoveRule() = component.checkForFiftyMoveRule(match)
    fun undoLastMove() = component.undoLastMove(match)

    internal fun callSpawnedEvent(p: CapturedPiece) = callEvent(captured.createSpawnedEvent(p))
    internal fun callSpawnEvent(p: CapturedPiece) = callEvent(captured.createSpawnEvent(p))
    internal fun callClearEvent(p: CapturedPiece) = callEvent(captured.createClearEvent(p))
}