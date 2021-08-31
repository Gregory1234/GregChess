package gregc.gregchess.chess.variant

import gregc.gregchess.*
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Chessboard
import gregc.gregchess.chess.component.Component
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable(with = ChessVariant.Serializer::class)
open class ChessVariant: NameRegistered {

    object Serializer: NameRegisteredSerializer<ChessVariant>("ChessVariant", RegistryType.VARIANT)

    override val module get() = RegistryType.VARIANT.getModule(this)
    override val name get() = RegistryType.VARIANT[this]

    override fun toString(): String = "${module.namespace}:$name@${hashCode().toString(16)}"

    enum class MoveLegality(val prettyName: String) {
        INVALID("Invalid moves"),
        IN_CHECK("Moves blocked because of checks"),
        PINNED("Moves blocked by pins"),
        SPECIAL("Moves blocked for other reasons"),
        LEGAL("Legal moves")
    }

    open fun start(game: ChessGame) {}

    open fun chessboardSetup(board: Chessboard) {}

    open fun finishMove(move: Move, game: ChessGame) {}

    open fun getLegality(move: Move, game: ChessGame): MoveLegality = with(move) {
        if (!Normal.isValid(move, game))
            return MoveLegality.INVALID

        if (piece.type == PieceType.KING) {
            return if (passedThrough.mapNotNull { game.board[it] }.all {
                    Normal.checkingMoves(!piece.side, it).isEmpty()
                }) MoveLegality.LEGAL else MoveLegality.IN_CHECK
        }

        val myKing = game.tryOrStopNull(game.board.kingOf(piece.side))
        val checks = Normal.checkingMoves(!piece.side, myKing.square)
        val capture = getTrait<CaptureTrait>()?.capture
        if (checks.any { ch -> capture != ch.piece.pos && startBlocking.none { it in ch.neededEmpty } })
            return MoveLegality.IN_CHECK
        val pins = Normal.pinningMoves(!piece.side, myKing.square)
        if (pins.any { pin ->
                pin.neededEmpty.filter { game.board[it]?.piece != null }.all { it in stopBlocking } && startBlocking.none { it in pin.neededEmpty }
            })
            return MoveLegality.PINNED
        return MoveLegality.LEGAL
    }

    open fun isInCheck(king: BoardPiece): Boolean = Normal.checkingMoves(!king.side, king.square).isNotEmpty()

    open fun checkForGameEnd(game: ChessGame) = with(game.board) {
        if (piecesOf(!game.currentTurn).all { getMoves(it.pos).none { m -> game.variant.isLegal(m, game) } }) {
            if (isInCheck(game, !game.currentTurn))
                game.stop(game.currentTurn.wonBy(EndReason.CHECKMATE))
            else
                game.stop(drawBy(EndReason.STALEMATE))
        }
        checkForRepetition()
        checkForFiftyMoveRule()
        val whitePieces = piecesOf(white)
        val blackPieces = piecesOf(white)
        if (whitePieces.size == 1 && blackPieces.size == 1)
            game.stop(drawBy(EndReason.INSUFFICIENT_MATERIAL))
        if (whitePieces.size == 2 && whitePieces.any { it.type.minor } && blackPieces.size == 1)
            game.stop(drawBy(EndReason.INSUFFICIENT_MATERIAL))
        if (blackPieces.size == 2 && blackPieces.any { it.type.minor } && whitePieces.size == 1)
            game.stop(drawBy(EndReason.INSUFFICIENT_MATERIAL))
    }

    open fun timeout(game: ChessGame, side: Side) {
        if (game.board.piecesOf(!side).size == 1)
            game.stop(drawBy(EndReason.DRAW_TIMEOUT))
        else
            game.stop(side.lostBy(EndReason.TIMEOUT))
    }

    open fun undoLastMove(move: Move, game: ChessGame) = move.undo(game)

    open fun getPieceMoves(piece: BoardPiece): List<Move> = piece.type.moveScheme.generate(piece)

    open fun isInCheck(game: ChessGame, side: Side): Boolean {
        val king = game.board.kingOf(side)
        return king != null && isInCheck(king)
    }

    fun isLegal(move: Move, game: ChessGame) = getLegality(move, game) == MoveLegality.LEGAL

    open fun genFEN(chess960: Boolean): FEN = if (!chess960) FEN() else FEN.generateChess960()

    open val pieceTypes: Collection<PieceType>
        get() = PieceType.run { listOf(KING, QUEEN, ROOK, BISHOP, KNIGHT, PAWN) }

    open val requiredComponents: Set<KClass<out Component>> get() = emptySet()

    open val optionalComponents: Set<KClass<out Component>> get() = emptySet()

    protected fun allMoves(side: Side, board: Chessboard) = board.piecesOf(side).flatMap { board.getMoves(it.pos) }

    object Normal : ChessVariant() {

        fun pinningMoves(by: Side, pos: Square) =
            allMoves(by, pos.board).filter { it.getTrait<CaptureTrait>()?.capture == pos.pos }.filter { m -> m.neededEmpty.any { pos.board[it]?.piece != null } }

        fun checkingMoves(by: Side, pos: Square) =
            allMoves(by, pos.board).filter { it.getTrait<CaptureTrait>()?.capture == pos.pos }.filter { m ->
                m.neededEmpty.mapNotNull { pos.board[it]?.piece }.all { it.side == !m.piece.side && it.type == PieceType.KING }
            }

        fun isValid(move: Move, game: ChessGame): Boolean = with(move) {
            val board = game.board
            if (flagsNeeded.any { (p, f) -> board[p].let { s -> s?.flags?.any { it.type == f && it.timeLeft >= 0 } == false } })
                return false

            if (neededEmpty.any { p -> board[p]?.piece != null })
                return false

            getTrait<CaptureTrait>()?.let {
                if (it.hasToCapture && board[it.capture]?.piece == null)
                    return false
                if (board[it.capture]?.piece?.side == piece.side)
                    return false
            }

            return true
        }

    }

}