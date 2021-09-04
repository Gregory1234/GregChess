package gregc.gregchess.chess.variant

import gregc.gregchess.GregChessModule
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Chessboard
import gregc.gregchess.register
import kotlinx.serialization.Serializable

object AtomicChess : ChessVariant() {

    class ExplosionEvent(val pos: Pos) : ChessEvent

    @Serializable
    class ExplosionTrait(val exploded: MutableList<CapturedBoardPiece> = mutableListOf()): MoveTrait {
        override val nameTokens: MoveName get() = MoveName()

        override val shouldComeBefore = listOf(CaptureTrait::class, TargetTrait::class, PromotionTrait::class)

        private fun BoardPiece.explode(by: Side, board: Chessboard) {
            exploded += capture(by, board)
        }

        override fun execute(game: ChessGame, move: Move, pass: UByte, remaining: List<MoveTrait>): Boolean {
            val captureTrait = move.getTrait<CaptureTrait>() ?: return true
            val targetTrait = move.getTrait<TargetTrait>() ?: return true
            if (captureTrait.captured == null)
                return true
            val pos = targetTrait.target
            game.board[pos]?.piece?.explode(move.piece.side, game.board)
            game.board[pos]?.neighbours()?.forEach {
                if (it.piece?.type != PieceType.PAWN)
                    it.piece?.explode(move.piece.side, game.board)
            }
            game.callEvent(ExplosionEvent(pos))
            return true
        }

        override fun undo(game: ChessGame, move: Move, pass: UByte, remaining: List<MoveTrait>): Boolean {
            for (p in exploded) {
                if (game.board[p.pos]?.piece != null)
                    return false
                p.resurrect(game.board)
            }
            return true
        }
    }

    @JvmField
    val ATOMIC = GregChessModule.register("atomic", DetEndReason(EndReason.Type.NORMAL))

    private fun nextToKing(side: Side, pos: Pos, board: Chessboard): Boolean =
        pos in board.kingOf(side)?.pos?.neighbours().orEmpty()

    private fun kingHug(board: Chessboard): Boolean {
        val wk = board.kingOf(white)?.pos
        return wk != null && nextToKing(black, wk, board)
    }

    private fun pinningMoves(by: Side, pos: Pos, board: Chessboard) =
        if (kingHug(board)) emptyList() else Normal.pinningMoves(by, pos, board)

    private fun checkingMoves(by: Side, pos: Pos, board: Chessboard) =
        if (nextToKing(by, pos, board)) emptyList() else Normal.checkingMoves(by, pos, board)

    override fun getPieceMoves(piece: BoardPiece, board: Chessboard): List<Move> = Normal.getPieceMoves(piece, board).map {
        if (it.getTrait<CaptureTrait>() != null) it.copy(traits = it.traits + ExplosionTrait()) else it
    }

    override fun getLegality(move: Move, game: ChessGame): MoveLegality = with(move) {

        if (!Normal.isValid(this, game))
            return MoveLegality.INVALID
        val captured = getTrait<CaptureTrait>()?.let { game.board[it.capture]?.piece }
        if (piece.type == PieceType.KING) {
            if (captured != null)
                return MoveLegality.SPECIAL

            if (passedThrough.all { checkingMoves(!piece.side, it, game.board).isEmpty() })
                MoveLegality.LEGAL
            else
                MoveLegality.IN_CHECK
        }

        val myKing = game.board.kingOf(piece.side) ?: return MoveLegality.IN_CHECK

        if (captured != null)
            if (myKing.pos in captured.pos.neighbours())
                return MoveLegality.SPECIAL

        val checks = checkingMoves(!piece.side, myKing.pos, game.board)
        val capture = getTrait<CaptureTrait>()?.capture
        if (checks.any { ch -> capture != ch.piece.pos && startBlocking.none { it in ch.neededEmpty } })
            return MoveLegality.IN_CHECK
        val pins = pinningMoves(!piece.side, myKing.pos, game.board)
        if (pins.any { pin ->
                capture != pin.piece.pos &&
                        pin.neededEmpty.filter { game.board[it]?.piece != null }
                            .all { it in stopBlocking } && startBlocking.none { it in pin.neededEmpty }
            })
            return MoveLegality.PINNED
        return MoveLegality.LEGAL
    }

    override fun isInCheck(king: BoardPiece, board: Chessboard): Boolean = checkingMoves(!king.side, king.pos, board).isNotEmpty()

    override fun checkForGameEnd(game: ChessGame) {
        if (game.board.kingOf(!game.currentTurn) == null)
            game.stop(game.currentTurn.wonBy(ATOMIC))
        Normal.checkForGameEnd(game)
    }
}