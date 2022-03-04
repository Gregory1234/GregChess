package gregc.gregchess.chess.variant

import gregc.gregchess.chess.*
import gregc.gregchess.chess.move.*
import gregc.gregchess.chess.piece.*
import gregc.gregchess.registry.Register
import gregc.gregchess.registry.Registering
import kotlinx.serialization.Serializable

object AtomicChess : ChessVariant(), Registering {

    class ExplosionEvent(val pos: Pos) : ChessEvent

    @Serializable
    class ExplosionTrait : MoveTrait {
        override val type get() = EXPLOSION

        var explodedNumber = 0
            private set

        override val shouldComeBefore get() = setOf(MoveTraitType.CAPTURE, MoveTraitType.TARGET, MoveTraitType.PROMOTION)

        private fun BoardPiece.explode(by: Color, tracker: PieceTracker): Pair<BoardPiece, CapturedPiece> {
            tracker.giveName("exploded${explodedNumber++}", this)
            return capture(by)
        }

        override fun execute(env: MoveEnvironment, move: Move) {
            val captureTrait = move.captureTrait ?: throw TraitPreconditionException(this, "No capture trait")
            if (!captureTrait.captureSuccess) return
            val explosions = mutableListOf<Pair<BoardPiece, CapturedPiece>>()
            val piece = move.main.boardPiece()
            explosions += move.main.boardPiece().explode(piece.color, move.pieceTracker)
            piece.pos.neighbours().mapNotNull { env[it] }.forEach {
                if (it.type != PieceType.PAWN)
                    explosions += it.explode(piece.color, move.pieceTracker)
            }
            move.pieceTracker.traceMove(env, *explosions.toTypedArray())
            env.callEvent(ExplosionEvent(piece.pos))
        }

        override fun undo(env: MoveEnvironment, move: Move) = tryPiece {
            move.pieceTracker.traceMoveBack(
                env,
                *((0 until explodedNumber).map { move.pieceTracker["exploded$it"] } + move.pieceTracker["main"]).toTypedArray())
        }
    }

    @JvmField
    @Register
    val ATOMIC = DetEndReason(EndReason.Type.NORMAL)

    @JvmField
    @Register
    val EXPLOSION = MoveTraitType(ExplosionTrait.serializer())

    private fun nextToKing(color: Color, pos: Pos, board: ChessboardView): Boolean =
        pos in board.kingOf(color)?.pos?.neighbours().orEmpty()

    private fun kingHug(board: ChessboardView): Boolean {
        val wk = board.kingOf(Color.WHITE)?.pos
        return wk != null && nextToKing(Color.BLACK, wk, board)
    }

    private fun pinningMoves(by: Color, pos: Pos, board: ChessboardView) =
        if (kingHug(board)) emptyList() else Normal.pinningMoves(by, pos, board)

    private fun checkingMoves(by: Color, pos: Pos, board: ChessboardView) =
        if (nextToKing(by, pos, board)) emptyList() else Normal.checkingMoves(by, pos, board)

    override fun getPieceMoves(piece: BoardPiece, board: ChessboardView): List<Move> =
        Normal.getPieceMoves(piece, board).map {
            if (it.captureTrait != null) it.copy(traits = it.traits + ExplosionTrait()) else it
        }

    override fun getLegality(move: Move, board: ChessboardView): MoveLegality = with(move) {

        if (!Normal.isValid(this, board))
            return MoveLegality.INVALID

        val captured = captureTrait?.let { board[it.capture] }

        if (main.type == PieceType.KING) {
            if (captured != null)
                return MoveLegality.SPECIAL

            return if (passedThrough.all { checkingMoves(!main.color, it, board).isEmpty() })
                MoveLegality.LEGAL
            else
                MoveLegality.IN_CHECK
        }

        val myKing = board.kingOf(main.color) ?: return MoveLegality.IN_CHECK

        if (captured != null)
            if (myKing.pos in captured.pos.neighbours())
                return MoveLegality.SPECIAL

        val checks = checkingMoves(!main.color, myKing.pos, board)
        val capture = captureTrait?.capture
        if (checks.any { ch -> capture != ch.origin && startBlocking.none { it in ch.neededEmpty } })
            return MoveLegality.IN_CHECK

        val pins = pinningMoves(!main.color, myKing.pos, board)
        if (pins.any { pin -> isPinnedBy(pin, board) })
            return MoveLegality.PINNED

        return MoveLegality.LEGAL
    }

    override fun isInCheck(king: BoardPiece, board: ChessboardView): Boolean =
        checkingMoves(!king.color, king.pos, board).isNotEmpty()

    override fun checkForGameEnd(game: ChessGame) {
        if (game.board.kingOf(!game.currentTurn) == null)
            game.stop(game.currentTurn.wonBy(ATOMIC))
        Normal.checkForGameEnd(game)
    }
}