package gregc.gregchess.chess.variant

import gregc.gregchess.GregChess
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Chessboard
import gregc.gregchess.chess.move.*
import gregc.gregchess.chess.piece.*
import gregc.gregchess.registerEndReason
import gregc.gregchess.registry.Register
import kotlinx.serialization.Serializable

object AtomicChess : ChessVariant() {

    class ExplosionEvent(val pos: Pos) : ChessEvent

    @Serializable
    class ExplosionTrait : MoveTrait {
        override val nameTokens: MoveName = MoveName(emptyMap())

        var explodedNumber = 0
            private set

        override val shouldComeBefore get() = listOf(CaptureTrait::class, TargetTrait::class, PromotionTrait::class)

        private fun BoardPiece.explode(by: Color, tracker: PieceTracker): Pair<BoardPiece, CapturedPiece> {
            tracker.giveName("exploded${explodedNumber++}", this)
            return capture(by)
        }

        override fun execute(game: ChessGame, move: Move) {
            val captureTrait = move.getTrait<CaptureTrait>() ?: throw TraitPreconditionException(this, "No capture trait")
            if (!captureTrait.captureSuccess) return
            val explosions = mutableListOf<Pair<BoardPiece, CapturedPiece>>()
            val piece = move.main.boardPiece()
            explosions += move.main.boardPiece().explode(piece.color, move.pieceTracker)
            piece.pos.neighbours().mapNotNull { game.board[it] }.forEach {
                if (it.type != PieceType.PAWN)
                    explosions += it.explode(piece.color, move.pieceTracker)
            }
            move.pieceTracker.traceMove(game.board, *explosions.toTypedArray())
            game.callEvent(ExplosionEvent(piece.pos))
        }

        override fun undo(game: ChessGame, move: Move) = tryPiece {
            move.pieceTracker.traceMoveBack(game.board,
                *((0 until explodedNumber).map { move.pieceTracker["exploded$it"] } + move.pieceTracker["main"]).toTypedArray())
        }
    }

    @JvmField
    @Register
    val ATOMIC = DetEndReason(EndReason.Type.NORMAL)

    private fun nextToKing(color: Color, pos: Pos, board: Chessboard): Boolean =
        pos in board.kingOf(color)?.pos?.neighbours().orEmpty()

    private fun kingHug(board: Chessboard): Boolean {
        val wk = board.kingOf(Color.WHITE)?.pos
        return wk != null && nextToKing(Color.BLACK, wk, board)
    }

    private fun pinningMoves(by: Color, pos: Pos, board: Chessboard) =
        if (kingHug(board)) emptyList() else Normal.pinningMoves(by, pos, board)

    private fun checkingMoves(by: Color, pos: Pos, board: Chessboard) =
        if (nextToKing(by, pos, board)) emptyList() else Normal.checkingMoves(by, pos, board)

    override fun getPieceMoves(piece: BoardPiece, board: Chessboard): List<Move> =
        Normal.getPieceMoves(piece, board).map {
            if (it.getTrait<CaptureTrait>() != null) it.copy(traits = it.traits + ExplosionTrait()) else it
        }

    override fun getLegality(move: Move, game: ChessGame): MoveLegality = with(move) {

        if (!Normal.isValid(this, game))
            return MoveLegality.INVALID

        val captured = getTrait<CaptureTrait>()?.let { game.board[it.capture] }

        if (main.type == PieceType.KING) {
            if (captured != null)
                return MoveLegality.SPECIAL

            return if (passedThrough.all { checkingMoves(!main.color, it, game.board).isEmpty() })
                MoveLegality.LEGAL
            else
                MoveLegality.IN_CHECK
        }

        val myKing = game.board.kingOf(main.color) ?: return MoveLegality.IN_CHECK

        if (captured != null)
            if (myKing.pos in captured.pos.neighbours())
                return MoveLegality.SPECIAL

        val checks = checkingMoves(!main.color, myKing.pos, game.board)
        val capture = getTrait<CaptureTrait>()?.capture
        if (checks.any { ch -> capture != ch.origin && startBlocking.none { it in ch.neededEmpty } })
            return MoveLegality.IN_CHECK

        val pins = pinningMoves(!main.color, myKing.pos, game.board)
        if (pins.any { pin -> isPinnedBy(pin, game.board) })
            return MoveLegality.PINNED

        return MoveLegality.LEGAL
    }

    override fun isInCheck(king: BoardPiece, board: Chessboard): Boolean =
        checkingMoves(!king.color, king.pos, board).isNotEmpty()

    override fun checkForGameEnd(game: ChessGame) {
        if (game.board.kingOf(!game.currentTurn) == null)
            game.stop(game.currentTurn.wonBy(ATOMIC))
        Normal.checkForGameEnd(game)
    }
}