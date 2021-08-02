package gregc.gregchess.chess.variant

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Chessboard
import gregc.gregchess.chess.component.Component
import kotlin.reflect.KClass

open class ChessVariant(val name: String) {

    override fun toString(): String = name

    enum class MoveLegality(val prettyName: String) {
        INVALID("Invalid moves"),
        IN_CHECK("Moves blocked because of checks"),
        PINNED("Moves blocked by pins"),
        SPECIAL("Moves blocked for other reasons"),
        LEGAL("Legal moves")
    }

    open fun start(game: ChessGame) {}

    open fun chessboardSetup(board: Chessboard) {}

    open fun finishMove(move: MoveCandidate) {}

    open fun getLegality(move: MoveCandidate): MoveLegality = with(move) {
        if (!Normal.isValid(move))
            return MoveLegality.INVALID

        if (piece.type == PieceType.KING) {
            return if ((pass + target.pos).mapNotNull { game.board[it] }.all {
                    Normal.checkingMoves(!piece.side, it).isEmpty()
                }) MoveLegality.LEGAL else MoveLegality.IN_CHECK
        }

        val myKing = game.tryOrStopNull(game.board.kingOf(piece.side))
        val checks = Normal.checkingMoves(!piece.side, myKing.square)
        if (checks.any { target.pos !in it.pass && target != it.origin })
            return MoveLegality.IN_CHECK
        val pins = Normal.pinningMoves(!piece.side, myKing.square).filter { origin.pos in it.pass }
        if (pins.any { target.pos !in it.pass && target != it.origin })
            return MoveLegality.PINNED
        return MoveLegality.LEGAL
    }

    open fun isInCheck(king: BoardPiece): Boolean = Normal.checkingMoves(!king.side, king.square).isNotEmpty()

    open fun checkForGameEnd(game: ChessGame) = with(game.board) {
        if (piecesOf(!game.currentTurn).all { getMoves(it.pos).none(game.variant::isLegal) }) {
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

    open fun undoLastMove(move: MoveData) = move.undo()

    open fun getPieceMoves(piece: BoardPiece): List<MoveCandidate> = piece.type.moveScheme.generate(piece)

    open fun isInCheck(game: ChessGame, side: Side): Boolean {
        val king = game.board.kingOf(side)
        return king != null && isInCheck(king)
    }

    fun isLegal(move: MoveCandidate) = getLegality(move) == MoveLegality.LEGAL

    open fun genFEN(chess960: Boolean): FEN = if (!chess960) FEN() else FEN.generateChess960()

    open val pieceTypes: Collection<PieceType>
        get() = PieceType.run { listOf(KING, QUEEN, ROOK, BISHOP, KNIGHT, PAWN) }

    open val requiredComponents: Collection<KClass<out Component.Settings<*>>>
        get() = emptyList()

    open val optionalComponents: Collection<KClass<out Component.Settings<*>>>
        get() = emptyList()

    protected fun allMoves(side: Side, board: Chessboard) = board.piecesOf(side).flatMap { board.getMoves(it.pos) }

    object Normal : ChessVariant("NORMAL") {

        fun pinningMoves(by: Side, pos: Square) =
            allMoves(by, pos.board).filter { it.control == pos }.filter { m -> m.blocks.count { it !in m.help } == 1 }

        fun checkingMoves(by: Side, pos: Square) =
            allMoves(by, pos.board).filter { it.control == pos }.filter { m ->
                m.needed.mapNotNull { m.board[it]?.piece }
                    .all { it.side == !m.piece.side && it.type == PieceType.KING || it in m.help }
            }

        fun isValid(move: MoveCandidate): Boolean = with(move) {
            if (flagsNeeded.any { (p, f) -> board[p].let { s -> s?.flags?.any { it.type == f && it.timeLeft >= 0 } == false } })
                return false

            if (needed.any { p -> board[p].let { it?.piece != null && it.piece !in help } })
                return false

            if (target.piece != null && control != target && target.piece !in help)
                return false

            if (captured == null && mustCapture)
                return false

            if (captured?.side == piece.side)
                return false

            return true
        }

    }

}