package gregc.gregchess.chess.variant

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Chessboard
import gregc.gregchess.chess.component.Component

object AtomicChess : ChessVariant("Atomic") {
    class ExplosionManager(private val game: ChessGame) : Component {
        private val explosions = mutableListOf<List<Pair<ChessPiece, ChessPiece.Captured>>>()

        fun explode(pos: ChessPosition) {
            val exp = mutableListOf<Pair<ChessPiece, ChessPiece.Captured>>()
            fun helper(p: ChessPiece) {
                exp += Pair(p, p.capture(game.currentTurn))
            }

            game.board[pos]?.piece?.let(::helper)
            game.board[pos]?.neighbours()?.forEach {
                if (it.piece?.type != ChessType.PAWN)
                    it.piece?.let(::helper)
            }
            game.renderer.doAt(pos) { world, l ->
                world.createExplosion(l, 4.0f, false, false)
            }
            explosions += exp
        }

        fun reverseExplosion() {
            val exp = explosions.last()
            exp.forEach { (p, c) ->
                p.resurrect(c)
            }
        }
    }

    class AtomicEndReason(winner: ChessSide) :
        ChessGame.EndReason("Chess.EndReason.Atomic", "normal", winner)

    override fun start(game: ChessGame) {
        game.registerComponent(ExplosionManager(game))
    }

    private fun nextToKing(side: ChessSide, pos: ChessPosition, board: Chessboard): Boolean =
        pos in board.kingOf(side)?.pos?.neighbours().orEmpty()

    private fun kingHug(board: Chessboard): Boolean {
        val wk = board.kingOf(ChessSide.WHITE)?.pos
        return wk != null && nextToKing(ChessSide.BLACK, wk, board)
    }

    private fun pinningMoves(by: ChessSide, pos: ChessSquare) =
        if (kingHug(pos.board)) emptyList() else Normal.pinningMoves(by, pos)

    private fun checkingMoves(by: ChessSide, pos: ChessSquare) =
        if (nextToKing(by, pos.pos, pos.board)) emptyList() else Normal.checkingMoves(by, pos)

    override fun finishMove(move: MoveCandidate) {
        if (move.captured != null)
            move.game.getComponent(ExplosionManager::class)?.explode(move.target.pos)
    }

    override fun getLegality(move: MoveCandidate): MoveLegality = move.run {

        if (!Normal.isValid(move))
            return MoveLegality.INVALID

        if (piece.type == ChessType.KING) {
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

    override fun isInCheck(king: ChessPiece): Boolean =
        checkingMoves(!king.side, king.square).isNotEmpty()

    override fun checkForGameEnd(game: ChessGame) {
        if (game.board.kingOf(!game.currentTurn) == null)
            game.stop(AtomicEndReason(game.currentTurn))
        Normal.checkForGameEnd(game)
    }

    override fun undoLastMove(move: MoveData) {
        if (move.captured)
            move.origin.game.getComponent(ExplosionManager::class)?.reverseExplosion()
        move.undo()
    }
}