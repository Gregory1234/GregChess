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

class SetFenEvent(val FEN: FEN) : ChessEvent

@Serializable
private class Square(
    var piece: BoardPiece? = null,
    val flags: MutableMap<ChessFlag, MutableList<Int>> = mutableMapOf()
) {
    @Transient var bakedMoves: List<Move>? = null
    @Transient var bakedLegalMoves: List<Move>? = null

    override fun toString() = "Square(piece=$piece, flags=$flags)"

    fun empty(board: Chessboard) {
        piece?.let(board::clear)
        bakedMoves = null
        bakedLegalMoves = null
        flags.clear()
    }

    fun copy(): Square = Square(piece, flags.mapValues { it.value.toMutableList() }.toMutableMap()).also { it.bakedMoves = bakedMoves; it.bakedLegalMoves = bakedLegalMoves }
}

class AddVariantOptionsEvent(private val options: MutableMap<ChessVariantOption<*>, Any>) : ChessEvent {
    operator fun <T : Any> set(option: ChessVariantOption<T>, value: T) = options.put(option, value)
}

@Serializable
private class BoardCounters(var halfmoveClock: Int, var fullmoveCounter: Int)

private class SquareChessboard(val initialFEN: FEN, private val variantOptions: Map<ChessVariantOption<*>, Any>, private val squares: Map<Pos, Square>, private val capturedPieces_: MutableList<CapturedPiece>, private val counters: BoardCounters) : ChessboardConnector {

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

    @Suppress("UNCHECKED_CAST")
    override operator fun <T : Any> get(option: ChessVariantOption<T>): T = variantOptions[option] as T

    override val chess960: Boolean
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

    override fun updateMoves(variant: ChessVariant) {
        for ((_, square) in squares) {
            square.bakedMoves = square.piece?.let { p -> variant.getPieceMoves(p, this) }
        }
        for ((_, square) in squares) {
            square.bakedLegalMoves = square.bakedMoves?.filter { variant.isLegal(it, this) }
        }
    }
}

@Serializable
class Chessboard private constructor (
    val initialFEN: FEN,
    private val simpleCastling: Boolean,
    private val squares: Map<Pos, Square>,
    private val counters: BoardCounters = BoardCounters(initialFEN.halfmoveClock, initialFEN.fullmoveCounter),
    @SerialName("boardHashes") private val boardHashes_: MutableMap<Int, Int> = mutableMapOf(initialFEN.hashed() to 1),
    @SerialName("capturedPieces") private val capturedPieces_: MutableList<CapturedPiece> = mutableListOf(),
    @SerialName("moveHistory") private val moveHistory_: MutableList<Move> = mutableListOf(),
    @Transient private val variantOptions: MutableMap<ChessVariantOption<*>, Any> = mutableMapOf(),
    @Transient private val internalBoard: SquareChessboard = SquareChessboard(initialFEN, variantOptions, squares, capturedPieces_, counters) // https://github.com/Kotlin/kotlinx.serialization/issues/241
) : Component, ChessboardConnector by internalBoard {
    private constructor(variant: ChessVariant, fen: FEN, simpleCastling: Boolean) : this(fen, simpleCastling, fen.toSquares(variant))
    constructor(variant: ChessVariant, fen: FEN? = null, chess960: Boolean = false, simpleCastling: Boolean = false) :
            this(variant, fen ?: variant.genFEN(chess960), simpleCastling)

    override val type get() = ComponentType.CHESSBOARD

    @Transient
    private lateinit var match: ChessMatch

    override fun init(match: ChessMatch) {
        this.match = match
    }

    var fullmoveCounter by counters::fullmoveCounter
        private set
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

    @ChessEventHandler
    fun endTurn(e: TurnEvent) {
        if (e == TurnEvent.END) {
            if (match.currentTurn == Color.BLACK) {
                fullmoveCounter++
            }
            for (s in squares.values)
                for (f in s.flags)
                    f.value.replaceAll { it + 1 }
            addBoardHash(getFEN().copy(currentTurn = !match.currentTurn))
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
            updateMoves()
    }

    @ChessEventHandler
    fun addVariantOptions(e: AddVariantOptionsEvent) {
        e[ChessVariantOption.SIMPLE_CASTLING] = simpleCastling
    }

    @Suppress("UNCHECKED_CAST")
    fun getVariantOptionStrings(): List<String> = variantOptions.mapNotNull { (o, v) -> (o as ChessVariantOption<Any>).pgnNameFragment(v) }

    @ChessEventHandler
    fun handleEvents(e: ChessBaseEvent) {
        if (e == ChessBaseEvent.SYNC || e == ChessBaseEvent.START) {
            match.callEvent(AddVariantOptionsEvent(variantOptions))
            updateMoves()
            pieces.forEach(::sendSpawned)
            capturedPieces.forEach(::sendSpawned)
        }
    }

    operator fun plusAssign(captured: CapturedPiece) {
        capturedPieces_ += captured
    }

    operator fun minusAssign(captured: CapturedPiece) {
        capturedPieces_.removeAt(capturedPieces_.lastIndexOf(captured))
    }

    fun updateMoves() = updateMoves(match.variant)

    fun setFromFEN(fen: FEN) {
        capturedPieces.asReversed().forEach(::clear)
        squares.values.forEach { it.empty(this) }
        fen.forEachSquare(match.variant) { p -> this += p }

        halfmoveClock = fen.halfmoveClock

        fullmoveCounter = fen.fullmoveCounter

        if (fen.currentTurn != match.currentTurn)
            match.nextTurn()


        if (fen.enPassantSquare != null) {
            set(fen.enPassantSquare, ChessFlag.EN_PASSANT, 1)
        }

        updateMoves()

        boardHashes_.clear()
        addBoardHash(fen)
        pieces.forEach(::sendSpawned)
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
            match.currentTurn,
            byColor(::castling),
            squares.entries.firstOrNull { (_, s) -> s.flags.any { it.key == ChessFlag.EN_PASSANT && it.flagActive } }?.key,
            halfmoveClock,
            fullmoveCounter
        )
    }

    fun checkForRepetition() {
        if ((boardHashes_[getFEN().copy(currentTurn = !match.currentTurn).hashed()] ?: 0) >= 3)
            match.stop(drawBy(EndReason.REPETITION))
    }

    fun checkForFiftyMoveRule() {
        if (halfmoveClock >= 100)
            match.stop(drawBy(EndReason.FIFTY_MOVES))
    }

    private fun addBoardHash(fen: FEN): Int {
        val hash = fen.hashed()
        boardHashes_[hash] = (boardHashes_[hash] ?: 0) + 1
        return boardHashes_[hash]!!
    }

    fun undoLastMove() {
        lastMove?.let {
            if (!it.isPhantomMove) {
                val hash = getFEN().hashed()
                boardHashes_[hash] = (boardHashes_[hash] ?: 1) - 1
                if (boardHashes_[hash] == 0) boardHashes_ -= hash
                match.undoMove(it)
                if (match.currentTurn == Color.WHITE)
                    fullmoveCounter--
                moveHistory_.removeLast()
                match.previousTurn()
            } else {
                match.undoMove(it)
                moveHistory_.removeLast()
            }
        }
    }

    // TODO: add a way of creating this without a Chessboard instance
    private class FakeChessboardConnector(val initialFEN: FEN, val variantOptions: Map<ChessVariantOption<*>, Any>, val squares: Map<Pos, Square>, val capturedPieces: MutableList<CapturedPiece>, val counters: BoardCounters)
        : ChessboardConnector by SquareChessboard(initialFEN, variantOptions, squares, capturedPieces, counters)

    @ChessEventHandler
    fun addFakeMoveConnectors(e: AddFakeMoveConnectorsEvent) {
        e[MoveConnectorType.CHESSBOARD] = FakeChessboardConnector(
            initialFEN, variantOptions,
            squares.mapValues { it.value.copy() }, capturedPieces.toMutableList(),
            BoardCounters(halfmoveClock, fullmoveCounter)
        )
    }

    @ChessEventHandler
    fun addMoveConnectors(e: AddMoveConnectorsEvent) {
        e[MoveConnectorType.CHESSBOARD] = this
    }

    internal fun clear(boardPiece: BoardPiece) {
        match.callEvent(createClearEvent(boardPiece))
    }

    internal fun clear(captured: CapturedPiece) {
        match.callEvent(this.captured.createClearEvent(captured))
    }

    internal fun sendSpawned(boardPiece: BoardPiece) {
        match.callEvent(createSpawnedEvent(boardPiece))
    }

    internal fun sendSpawned(captured: CapturedPiece) {
        match.callEvent(this.captured.createSpawnedEvent(captured))
    }

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

    }
}