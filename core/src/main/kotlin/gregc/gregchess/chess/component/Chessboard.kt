package gregc.gregchess.chess.component

import gregc.gregchess.chess.*
import gregc.gregchess.chess.move.*
import gregc.gregchess.chess.piece.*
import gregc.gregchess.chess.variant.ChessVariant
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

class CreateFakePieceHolderEvent(private val holders: MutableMap<PlacedPieceType<*, *>, PieceHolder<*>>) : ChessEvent {
    operator fun <P : PlacedPiece, H : PieceHolder<P>> set(option: PlacedPieceType<P, H>, value: PieceHolder<P>) = holders.put(option, value)
}

private class SquareChessboard(val initialFEN: FEN, private val variantOptions: Map<ChessVariantOption<*>, Any>, private val squares: Map<Pos, Square>) : BoardPieceHolder {

    override val pieces get() = squares.values.mapNotNull { it.piece }

    override operator fun get(pos: Pos) = squares[pos]?.piece

    override fun getFlags(pos: Pos): Map<ChessFlag, Int> =
        squares[pos]?.flags?.filterValues { it.isNotEmpty() }?.mapValues { it.value.last() }.orEmpty()

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

    override fun addFlag(pos: Pos, flag: ChessFlag, age: Int) {
        squares[pos]?.flags?.let {
            it[flag] = it[flag] ?: mutableListOf()
            it[flag]!! += age
        }
    }
}

@Serializable
class Chessboard private constructor (
    val initialFEN: FEN,
    private val simpleCastling: Boolean,
    private val squares: Map<Pos, Square>,
    var halfmoveClock: Int = initialFEN.halfmoveClock,
    @SerialName("fullmoveCounter") private var fullmoveCounter_: Int = initialFEN.fullmoveCounter,
    @SerialName("boardHashes") private val boardHashes_: MutableMap<Int, Int> = mutableMapOf(initialFEN.hashed() to 1),
    @SerialName("capturedPieces") private val capturedPieces_: MutableList<CapturedPiece> = mutableListOf(),
    @SerialName("moveHistory") private val moveHistory_: MutableList<Move> = mutableListOf(),
    @Transient private val variantOptions: MutableMap<ChessVariantOption<*>, Any> = mutableMapOf(),
    @Transient private val internalBoard: SquareChessboard = SquareChessboard(initialFEN, variantOptions, squares) // https://github.com/Kotlin/kotlinx.serialization/issues/241
) : Component, BoardPieceHolder by internalBoard, PieceEventCaller {
    private constructor(variant: ChessVariant, fen: FEN, simpleCastling: Boolean) : this(fen, simpleCastling, fen.toSquares(variant))
    constructor(variant: ChessVariant, fen: FEN? = null, chess960: Boolean = false, simpleCastling: Boolean = false) :
            this(variant, fen ?: variant.genFEN(chess960), simpleCastling)

    override val type get() = ComponentType.CHESSBOARD

    @Transient
    private lateinit var game: ChessGame

    override fun init(game: ChessGame) {
        this.game = game
    }

    val fullmoveCounter get() = fullmoveCounter_
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
            if (game.currentTurn == Color.BLACK) {
                fullmoveCounter_++
            }
            for (s in squares.values)
                for (f in s.flags)
                    f.value.replaceAll { it + 1 }
            addBoardHash(getFEN().copy(currentTurn = !game.currentTurn))
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
    fun handleEvents(e: GameBaseEvent) {
        if (e == GameBaseEvent.SYNC || e == GameBaseEvent.START) {
            game.callEvent(AddVariantOptionsEvent(variantOptions))
            updateMoves()
            pieces.forEach(::sendSpawned)
            capturedPieces.forEach(CapturedPieceHolder()::sendSpawned)
        }
    }

    operator fun plusAssign(captured: CapturedPiece) {
        capturedPieces_ += captured
    }

    operator fun minusAssign(captured: CapturedPiece) {
        capturedPieces_.removeAt(capturedPieces_.lastIndexOf(captured))
    }

    fun updateMoves() {
        for ((_, square) in squares) {
            square.bakedMoves = square.piece?.let { p -> game.variant.getPieceMoves(p, this) }
        }
        for ((_, square) in squares) {
            square.bakedLegalMoves = square.bakedMoves?.filter { game.variant.isLegal(it, this) }
        }
    }

    fun setFromFEN(fen: FEN) {
        capturedPieces.asReversed().forEach(CapturedPieceHolder()::clear)
        squares.values.forEach { it.empty(this) }
        fen.forEachSquare(game.variant) { p -> this += p }

        halfmoveClock = fen.halfmoveClock

        fullmoveCounter_ = fen.fullmoveCounter

        if (fen.currentTurn != game.currentTurn)
            game.nextTurn()


        if (fen.enPassantSquare != null) {
            addFlag(fen.enPassantSquare, ChessFlag.EN_PASSANT, 1)
        }

        updateMoves()

        boardHashes_.clear()
        addBoardHash(fen)
        pieces.forEach(::sendSpawned)
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
            squares.entries.firstOrNull { (_, s) -> s.flags.any { it.key == ChessFlag.EN_PASSANT && it.flagActive } }?.key,
            halfmoveClock,
            fullmoveCounter_
        )
    }

    fun checkForRepetition() {
        if ((boardHashes_[getFEN().copy(currentTurn = !game.currentTurn).hashed()] ?: 0) >= 3)
            game.stop(drawBy(EndReason.REPETITION))
    }

    fun checkForFiftyMoveRule() {
        if (halfmoveClock >= 100)
            game.stop(drawBy(EndReason.FIFTY_MOVES))
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
                game.undoMove(it)
                if (game.currentTurn == Color.WHITE)
                    fullmoveCounter_--
                moveHistory_.removeLast()
                game.previousTurn()
            } else {
                game.undoMove(it)
                moveHistory_.removeLast()
            }
        }
    }

    private inner class CapturedPieceHolder: PieceHolder<CapturedPiece>, PieceEventCaller {

        override fun exists(p: CapturedPiece) = this@Chessboard.capturedPieces.any { it == p }

        override fun canExist(p: CapturedPiece) = true

        override fun create(p: CapturedPiece) {
            this@Chessboard += p
        }

        override fun destroy(p: CapturedPiece) {
            checkExists(p)
            this@Chessboard -= p
        }

        override fun callPieceMoveEvent(e: PieceMoveEvent) = game.callEvent(e)

        override val pieces get() = capturedPieces
    }

    @ChessEventHandler
    fun addPieceHolders(e: AddPieceHoldersEvent) {
        e[PlacedPieceType.BOARD] = this
        e[PlacedPieceType.CAPTURED] = CapturedPieceHolder()
    }

    override fun callPieceMoveEvent(e: PieceMoveEvent) = game.callEvent(e)

    // TODO: add a way of creating this without a Chessboard instance
    private class FakeBoardPieceHolder(val initialFEN: FEN, val variantOptions: Map<ChessVariantOption<*>, Any>, val squares: Map<Pos, Square>)
        : BoardPieceHolder by SquareChessboard(initialFEN, variantOptions, squares)

    private class FakeCapturedPieceHolder(val capturedPieces : MutableList<CapturedPiece>) : PieceHolder<CapturedPiece> {
        override fun exists(p: CapturedPiece) = capturedPieces.any { it == p }

        override fun canExist(p: CapturedPiece) = true

        override fun create(p: CapturedPiece) {
            capturedPieces += p
        }

        override fun destroy(p: CapturedPiece) {
            checkExists(p)
            capturedPieces.removeAt(capturedPieces.lastIndexOf(p))
        }

        override val pieces get() = capturedPieces
    }

    @ChessEventHandler
    fun createFakePieceHolder(e: CreateFakePieceHolderEvent) {
        e[PlacedPieceType.BOARD] = FakeBoardPieceHolder(initialFEN, variantOptions, squares.mapValues { it.value.copy() })
        e[PlacedPieceType.CAPTURED] = FakeCapturedPieceHolder(capturedPieces_.toMutableList())
    }

    fun createSimpleMoveEnvironment(): MoveEnvironment {
        val holders = mutableMapOf<PlacedPieceType<*, *>, PieceHolder<*>>()
        game.callEvent(CreateFakePieceHolderEvent(holders))
        val board = holders[PlacedPieceType.BOARD] as FakeBoardPieceHolder
        return SimpleMoveEnvironment(game.variant, holders, board)
    }

    private class SimpleMoveEnvironment(override val variant: ChessVariant, val holders: MutableMap<PlacedPieceType<*, *>, PieceHolder<*>>, val board: FakeBoardPieceHolder) : MoveEnvironment {
        override fun updateMoves() {
            for ((_, square) in board.squares) {
                square.bakedMoves = square.piece?.let { p -> variant.getPieceMoves(p, board) }
            }
            for ((_, square) in board.squares) {
                square.bakedLegalMoves = square.bakedMoves?.filter { variant.isLegal(it, board) }
            }
        }

        override fun callEvent(e: ChessEvent) {}

        override val pieces get() = holders.flatMap { it.value.pieces }

        override fun <T : Component> get(type: ComponentType<T>): T? = null

        @Suppress("UNCHECKED_CAST")
        override fun <P : PlacedPiece, H : PieceHolder<P>> get(p: PlacedPieceType<P, H>): H = holders[p]!! as H
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

val ComponentHolder.board get() = get(ComponentType.CHESSBOARD)