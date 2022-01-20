package gregc.gregchess.chess.variant

import gregc.gregchess.ChessModule
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Chessboard
import gregc.gregchess.chess.component.ComponentType
import gregc.gregchess.chess.move.*
import gregc.gregchess.chess.piece.*
import gregc.gregchess.registry.*
import gregc.gregchess.rotationsOf
import kotlinx.serialization.Serializable

@Serializable(with = ChessVariant.Serializer::class)
open class ChessVariant : NameRegistered {

    object Serializer : NameRegisteredSerializer<ChessVariant>("ChessVariant", Registry.VARIANT)

    final override val key get() = Registry.VARIANT[this]

    final override fun toString(): String = Registry.VARIANT.simpleElementToString(this)

    enum class MoveLegality(val prettyName: String) {
        INVALID("Invalid moves"),
        IN_CHECK("Moves blocked because of checks"),
        PINNED("Moves blocked by pins"),
        SPECIAL("Moves blocked for other reasons"),
        LEGAL("Legal moves")
    }

    open fun getLegality(move: Move, game: ChessGame): MoveLegality = with(move) {
        if (!Normal.isValid(move, game))
            return MoveLegality.INVALID

        if (main.type == PieceType.KING) {
            return if (passedThrough.all { Normal.checkingMoves(!main.color, it, game.board).isEmpty() })
                MoveLegality.LEGAL
            else
                MoveLegality.IN_CHECK
        }

        val myKing = game.tryOrStopNull(game.board.kingOf(main.color))
        val checks = Normal.checkingMoves(!main.color, myKing.pos, game.board)
        val capture = getTrait<CaptureTrait>()?.capture
        if (checks.any { ch -> capture != ch.origin && startBlocking.none { it in ch.neededEmpty } })
            return MoveLegality.IN_CHECK

        val pins = Normal.pinningMoves(!main.color, myKing.pos, game.board)
        if (pins.any { pin -> isPinnedBy(pin, game.board) })
            return MoveLegality.PINNED

        return MoveLegality.LEGAL
    }

    fun isLegal(move: Move, game: ChessGame) = getLegality(move, game) == MoveLegality.LEGAL

    open fun isInCheck(king: BoardPiece, board: Chessboard): Boolean =
        Normal.checkingMoves(!king.color, king.pos, board).isNotEmpty()

    open fun isInCheck(game: ChessGame, color: Color): Boolean {
        val king = game.board.kingOf(color)
        return king != null && isInCheck(king, game.board)
    }

    open fun checkForGameEnd(game: ChessGame) = with(game.board) {
        if (piecesOf(!game.currentTurn).all { it.getMoves(this).none { m -> game.variant.isLegal(m, game) } }) {
            if (isInCheck(game, !game.currentTurn))
                game.stop(game.currentTurn.wonBy(EndReason.CHECKMATE))
            else
                game.stop(drawBy(EndReason.STALEMATE))
        }

        checkForRepetition()
        checkForFiftyMoveRule()

        val whitePieces = piecesOf(Color.WHITE)
        val blackPieces = piecesOf(Color.BLACK)
        if (whitePieces.size == 1 && blackPieces.size == 1)
            game.stop(drawBy(EndReason.INSUFFICIENT_MATERIAL))

        val minorPieces = listOf(PieceType.KNIGHT, PieceType.BISHOP)
        if (whitePieces.size == 2 && whitePieces.any { it.type in minorPieces } && blackPieces.size == 1)
            game.stop(drawBy(EndReason.INSUFFICIENT_MATERIAL))
        if (blackPieces.size == 2 && blackPieces.any { it.type in minorPieces } && whitePieces.size == 1)
            game.stop(drawBy(EndReason.INSUFFICIENT_MATERIAL))
    }

    open fun timeout(game: ChessGame, color: Color) {
        if (game.board.piecesOf(!color).size == 1)
            game.stop(drawBy(EndReason.DRAW_TIMEOUT))
        else
            game.stop(color.lostBy(EndReason.TIMEOUT))
    }

    open fun getPieceMoves(piece: BoardPiece, board: Chessboard): List<Move> = when(piece.type) {
        PieceType.KING -> kingMovement(piece, board)
        PieceType.QUEEN -> rays(piece, rotationsOf(1, 1) + rotationsOf(1, 0))
        PieceType.ROOK -> rays(piece, rotationsOf(1, 0))
        PieceType.BISHOP -> rays(piece, rotationsOf(1, 1))
        PieceType.KNIGHT -> jumps(piece, rotationsOf(2, 1))
        PieceType.PAWN -> pawnMovement(piece).promotions(Normal.PROMOTIONS)
        else -> throw IllegalArgumentException(piece.type.toString())
    }

    open fun startingPieceHasMoved(fen: FEN, pos: Pos, piece: Piece): Boolean = when(piece.type) {
        PieceType.PAWN -> when (piece.color) {
            Color.WHITE -> pos.rank != 1
            Color.BLACK -> pos.rank != 6
        }
        PieceType.ROOK -> pos.file !in fen.castlingRights[piece.color]
        else -> false
    }

    open fun genFEN(chess960: Boolean): FEN = if (!chess960) FEN() else FEN.generateChess960()

    open val pieceTypes: Collection<PieceType>
        get() = PieceType.run { listOf(KING, QUEEN, ROOK, BISHOP, KNIGHT, PAWN) }

    open val requiredComponents: Set<ComponentType<*>> get() = emptySet()

    open val optionalComponents: Set<ComponentType<*>> get() = emptySet()

    open val pgnNameFormatter: MoveNameFormatter = MoveNameFormatter { name ->
        buildString {
            name.getOrNull(MoveNameTokenType.PIECE_TYPE)?.let { append(it.char.uppercase()) }
            name.getOrNull(MoveNameTokenType.UNIQUENESS_COORDINATE)?.let { append(it) }
            name.getOrNull(MoveNameTokenType.CAPTURE)?.let { append("x") }
            name.getOrNull(MoveNameTokenType.TARGET)?.let { append(it) }
            name.getOrNull(MoveNameTokenType.CASTLE)?.let { append(it.castles) }
            name.getOrNull(MoveNameTokenType.PROMOTION)?.let { append("="+it.char.uppercase()) }
            name.getOrNull(MoveNameTokenType.CHECK)?.let { append("+") }
            name.getOrNull(MoveNameTokenType.CHECKMATE)?.let { append("#") }
        }
    }

    protected fun allMoves(color: Color, board: Chessboard) = board.piecesOf(color).flatMap { it.getMoves(board) }

    protected fun Move.isPinnedBy(pin: Move, board: Chessboard) =
        getTrait<CaptureTrait>()?.capture != pin.origin &&
                pin.neededEmpty.filter { board[it] != null }.all { it in stopBlocking } &&
                startBlocking.none { it in pin.neededEmpty }

    open fun registerAll(module: ChessModule) {
        AutoRegister(module, AutoRegister.basicTypes).registerAll(this::class)
    }

    object Normal : ChessVariant() {

        @JvmField
        val PROMOTIONS = with(PieceType) { listOf(QUEEN, ROOK, BISHOP, KNIGHT) }

        fun pinningMoves(by: Color, pos: Pos, board: Chessboard) =
            allMoves(by, board).filter { it.getTrait<CaptureTrait>()?.capture == pos }.filter { m ->
                m.getTrait<RequireFlagTrait>()?.flags.orEmpty().none { (p, fs) -> !fs.all { f -> board.hasActiveFlag(p, f) } } &&
                        m.neededEmpty.any { board[it]?.piece != null }
            }

        fun checkingMoves(by: Color, pos: Pos, board: Chessboard) =
            allMoves(by, board).filter { it.getTrait<CaptureTrait>()?.capture == pos }.filter { m ->
                m.getTrait<RequireFlagTrait>()?.flags.orEmpty().none { (p, fs) -> !fs.all { f -> board.hasActiveFlag(p, f) } } &&
                        m.neededEmpty.mapNotNull { board[it]?.piece }
                            .all { it.color == !m.main.color && it.type == PieceType.KING }
            }

        fun isValid(move: Move, game: ChessGame): Boolean = with(move) {
            val board = game.board

            if (getTrait<RequireFlagTrait>()?.flags.orEmpty().any { (p, fs) -> !fs.all { f -> board.hasActiveFlag(p, f) } })
                return false

            if (neededEmpty.any { board[it] != null })
                return false

            getTrait<CaptureTrait>()?.let {
                if (it.hasToCapture && board[it.capture] == null)
                    return false
                if (board[it.capture]?.color == main.color)
                    return false
            }

            return true
        }

    }

}