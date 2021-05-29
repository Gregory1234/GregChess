package gregc.gregchess.chess.variant

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.*

object AtomicChess : ChessVariant("Atomic") {
    class ExplosionManager(private val game: ChessGame) : Component {
        private val explosions = mutableListOf<List<Pair<BoardPiece, CapturedPiece>>>()

        fun explode(pos: Pos) {
            val exp = mutableListOf<Pair<BoardPiece, CapturedPiece>>()
            fun helper(p: BoardPiece) {
                exp += Pair(p, p.capture(game.currentTurn))
            }

            game.board[pos]?.piece?.let(::helper)
            game.board[pos]?.neighbours()?.forEach {
                if (it.piece?.type != PieceType.PAWN)
                    it.piece?.let(::helper)
            }
            game.renderers.forEach { it.explosionAt(pos) }
            explosions += exp
        }

        fun reverseExplosion() {
            val exp = explosions.last()
            exp.forEach { (p, c) ->
                p.resurrect(c)
            }
        }
    }

    class AtomicEndReason(winner: Side) :
        ChessGame.EndReason("Chess.EndReason.Atomic", "normal", winner)

    override fun start(game: ChessGame) {
        game.registerComponent(ExplosionManager(game))
    }

    private fun nextToKing(side: Side, pos: Pos, board: Chessboard): Boolean =
        pos in board.kingOf(side)?.pos?.neighbours().orEmpty()

    private fun kingHug(board: Chessboard): Boolean {
        val wk = board.kingOf(Side.WHITE)?.pos
        return wk != null && nextToKing(Side.BLACK, wk, board)
    }

    private fun pinningMoves(by: Side, pos: Square) =
        if (kingHug(pos.board)) emptyList() else Normal.pinningMoves(by, pos)

    private fun checkingMoves(by: Side, pos: Square) =
        if (nextToKing(by, pos.pos, pos.board)) emptyList() else Normal.checkingMoves(by, pos)

    override fun finishMove(move: MoveCandidate) {
        if (move.captured != null)
            move.game.getComponent<ExplosionManager>()?.explode(move.target.pos)
    }

    override fun getLegality(move: MoveCandidate): MoveLegality = move.run {

        if (!Normal.isValid(move))
            return MoveLegality.INVALID

        if (piece.type == PieceType.KING) {
            if (move.captured != null)
                return MoveLegality.SPECIAL

            if ((pass + target.pos).mapNotNull { game.board[it] }.all {
                    checkingMoves(!piece.side, it).isEmpty()
                }) MoveLegality.LEGAL else MoveLegality.IN_CHECK
        }

        val myKing = game.board.kingOf(piece.side) ?: return MoveLegality.IN_CHECK

        if (move.captured != null)
            if (myKing.pos in move.target.pos.neighbours())
                return MoveLegality.SPECIAL

        val checks = checkingMoves(!piece.side, myKing.square)
        if (checks.any { target.pos !in it.pass && target != it.origin })
            return MoveLegality.IN_CHECK
        val pins = pinningMoves(!piece.side, myKing.square).filter { origin.pos in it.pass }
        if (pins.any { target.pos !in it.pass && target != it.origin })
            return MoveLegality.PINNED
        return MoveLegality.LEGAL
    }

    override fun isInCheck(king: BoardPiece): Boolean = checkingMoves(!king.side, king.square).isNotEmpty()

    override fun checkForGameEnd(game: ChessGame) {
        if (game.board.kingOf(!game.currentTurn) == null)
            game.stop(AtomicEndReason(game.currentTurn))
        Normal.checkForGameEnd(game)
    }

    override fun undoLastMove(move: MoveData) {
        if (move.captured)
            move.origin.game.getComponent<ExplosionManager>()?.reverseExplosion()
        move.undo()
    }
}