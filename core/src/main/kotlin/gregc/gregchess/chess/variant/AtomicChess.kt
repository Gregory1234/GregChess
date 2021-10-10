package gregc.gregchess.chess.variant

import gregc.gregchess.GregChessModule
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Chessboard
import gregc.gregchess.chess.move.*
import gregc.gregchess.chess.piece.*
import gregc.gregchess.register
import kotlinx.serialization.Serializable

object AtomicChess : ChessVariant() {

    class ExplosionEvent(val pos: Pos) : ChessEvent

    @Serializable
    class ExplosionTrait(private val exploded: MutableList<CapturedBoardPiece> = mutableListOf()) : MoveTrait {
        override val nameTokens: MoveName = nameOf()

        override val shouldComeBefore get() = listOf(CaptureTrait::class, TargetTrait::class, PromotionTrait::class)

        private fun BoardPiece.explode(by: Color, board: Chessboard) {
            exploded += capture(by, board)
        }

        override fun execute(game: ChessGame, move: Move) {
            val captureTrait = move.getTrait<CaptureTrait>() ?: throw TraitPreconditionException(this, "No capture trait")
            val targetTrait = move.getTrait<TargetTrait>() ?: throw TraitPreconditionException(this, "No target trait")
            if (captureTrait.captured == null) return
            val pos = targetTrait.target
            game.board[pos]?.piece?.explode(move.piece.color, game.board)
            game.board[pos]?.neighbours()?.forEach {
                if (it.piece?.type != PieceType.PAWN)
                    it.piece?.explode(move.piece.color, game.board)
            }
            game.callEvent(ExplosionEvent(pos))
        }

        override fun undo(game: ChessGame, move: Move) = tryPiece {
            for (p in exploded) {
                p.resurrect(game.board)
            }
        }
    }

    @JvmField
    val ATOMIC = GregChessModule.register("atomic", DetEndReason(EndReason.Type.NORMAL))

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
        val captured = getTrait<CaptureTrait>()?.let { game.board[it.capture]?.piece }
        if (piece.type == PieceType.KING) {
            if (captured != null)
                return MoveLegality.SPECIAL

            if (passedThrough.all { checkingMoves(!piece.color, it, game.board).isEmpty() })
                MoveLegality.LEGAL
            else
                MoveLegality.IN_CHECK
        }

        val myKing = game.board.kingOf(piece.color) ?: return MoveLegality.IN_CHECK

        if (captured != null)
            if (myKing.pos in captured.pos.neighbours())
                return MoveLegality.SPECIAL

        val checks = checkingMoves(!piece.color, myKing.pos, game.board)
        val capture = getTrait<CaptureTrait>()?.capture
        if (checks.any { ch -> capture != ch.piece.pos && startBlocking.none { it in ch.neededEmpty } })
            return MoveLegality.IN_CHECK
        val pins = pinningMoves(!piece.color, myKing.pos, game.board)
        if (pins.any { pin ->
                capture != pin.piece.pos &&
                        pin.neededEmpty.filter { game.board[it]?.piece != null }
                            .all { it in stopBlocking } && startBlocking.none { it in pin.neededEmpty }
            })
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