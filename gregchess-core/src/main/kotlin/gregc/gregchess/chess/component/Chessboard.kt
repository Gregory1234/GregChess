package gregc.gregchess.chess.component

import gregc.gregchess.chess.*
import gregc.gregchess.chess.move.ChessVariantOption
import gregc.gregchess.chess.move.Move
import gregc.gregchess.chess.piece.*
import gregc.gregchess.chess.variant.ChessVariant
import gregc.gregchess.rangeTo
import kotlinx.serialization.*
import kotlin.collections.component1
import kotlin.collections.component2

class SetFenEvent(val FEN: FEN) : ChessEvent

@Serializable
private class Square(
    var piece: BoardPiece? = null,
    val flags: MutableMap<ChessFlag, MutableList<UInt>> = mutableMapOf()
) {
    @Transient var bakedMoves: List<Move>? = null
    @Transient var bakedLegalMoves: List<Move>? = null

    override fun toString() = "Square(piece=$piece, flags=$flags)"

    fun empty(board: Chessboard) {
        piece?.clear(board)
        bakedMoves = null
        bakedLegalMoves = null
        flags.clear()
    }
}

class AddVariantOptionsEvent(private val options: MutableMap<ChessVariantOption<*>, Any>) : ChessEvent {
    operator fun <T : Any> set(option: ChessVariantOption<T>, value: T) = options.put(option, value)
}

@Serializable
class Chessboard private constructor (
    val initialFEN: FEN,
    private val simpleCastling: Boolean,
    private val squares: Map<Pos, Square>,
    var halfmoveClock: UInt = initialFEN.halfmoveClock,
    @SerialName("fullmoveCounter") private var fullmoveCounter_: UInt = initialFEN.fullmoveCounter,
    @SerialName("boardHashes") private val boardHashes_: MutableMap<Int, Int> = mutableMapOf(initialFEN.hashed() to 1),
    @SerialName("capturedPieces") private val capturedPieces_: MutableList<CapturedPiece> = mutableListOf(),
    @SerialName("moveHistory") private val moveHistory_: MutableList<Move> = mutableListOf()
) : Component {
    private constructor(variant: ChessVariant, fen: FEN, simpleCastling: Boolean) : this(fen, simpleCastling, fen.toSquares(variant))
    constructor(variant: ChessVariant, fen: FEN? = null, chess960: Boolean = false, simpleCastling: Boolean = false) :
            this(variant, fen ?: variant.genFEN(chess960), simpleCastling)

    override val type get() = ComponentType.CHESSBOARD

    @Transient
    private lateinit var game: ChessGame

    override fun init(game: ChessGame) {
        this.game = game
    }

    @Transient
    private val variantOptions = mutableMapOf<ChessVariantOption<*>, Any>()

    val fullmoveCounter get() = fullmoveCounter_
    val boardHashes get() = boardHashes_.toMap()
    val capturedPieces get() = capturedPieces_.toList()
    val moveHistory get() = moveHistory_.toList()

    val pieces get() = squares.values.mapNotNull { it.piece }

    private val boardState
        get() = FEN.boardStateFromPieces(squares.mapNotNull { (p, s) -> s.piece?.let { Pair(p, it.piece) } }.toMap())

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
                    f.value.replaceAll { it + 1u }
            addBoardHash(getFEN().copy(currentTurn = !game.currentTurn))
        }
        if (e == TurnEvent.UNDO) {
            for (s in squares.values) {
                for (f in s.flags) {
                    f.value.replaceAll { it - 1u }
                    f.value.removeIf { it == 0u }
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
    operator fun <T : Any> get(option: ChessVariantOption<T>): T = variantOptions[option] as T

    @Suppress("UNCHECKED_CAST")
    fun getVariantOptionStrings(): List<String> = variantOptions.mapNotNull { (o, v) -> (o as ChessVariantOption<Any>).pgnNameFragment(v) }

    @ChessEventHandler
    fun handleEvents(e: GameBaseEvent) {
        if (e == GameBaseEvent.SYNC || e == GameBaseEvent.START) {
            game.callEvent(AddVariantOptionsEvent(variantOptions))
            updateMoves()
            pieces.forEach { it.sendCreated(this) }
        }
    }

    fun piecesOf(color: Color) = pieces.filter { it.color == color }
    fun piecesOf(color: Color, type: PieceType) = pieces.filter { it.color == color && it.type == type }

    fun kingOf(color: Color) = piecesOf(color).firstOrNull { it.type == PieceType.KING }

    operator fun plusAssign(captured: CapturedPiece) {
        capturedPieces_ += captured
    }

    operator fun minusAssign(captured: CapturedPiece) {
        capturedPieces_ -= captured
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
        squares.values.forEach { it.empty(this) }
        fen.forEachSquare(game.variant) { p -> this += p }

        halfmoveClock = fen.halfmoveClock

        fullmoveCounter_ = fen.fullmoveCounter

        if (fen.currentTurn != game.currentTurn)
            game.nextTurn()


        if (fen.enPassantSquare != null) {
            addFlag(fen.enPassantSquare, ChessFlag.EN_PASSANT, 1u)
        }

        updateMoves()

        boardHashes_.clear()
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
        if (halfmoveClock >= 100u)
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
                it.undo(game)
                if (game.currentTurn == Color.WHITE)
                    fullmoveCounter_--
                moveHistory_.removeLast()
                game.previousTurn()
            } else {
                it.undo(game)
                moveHistory_.removeLast()
            }
        }
    }

    fun callPieceEvent(e: PieceEvent) = game.callEvent(e)

    companion object {

        private fun FEN.toSquares(variant: ChessVariant): Map<Pos, Square> = toPieces(variant).mapValues { (pos, piece) ->
            Square(piece, if (pos == enPassantSquare) mutableMapOf(ChessFlag.EN_PASSANT to mutableListOf(1u)) else mutableMapOf())
        }.let {
            (Pair(0, 0)..Pair(7, 7)).associate { (i, j) -> Pos(i, j) to (it[Pos(i, j)] ?: Square()) }
        }

    }
}
