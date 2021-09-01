package gregc.gregchess.chess.variant

import gregc.gregchess.GregChessModule
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.*
import gregc.gregchess.register
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

object AtomicChess : ChessVariant() {

    class ExplosionEvent(val pos: Pos) : ChessEvent

    @Serializable
    object ExplosionManagerData : ComponentData<ExplosionManager> {
        override fun getComponent(game: ChessGame) = ExplosionManager(game, this)
    }

    class ExplosionManager(game: ChessGame, override val data: ExplosionManagerData) : Component(game) {

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
            game.callEvent(ExplosionEvent(pos))
            explosions += exp
        }

        fun reverseExplosion() {
            val exp = explosions.last()
            for ((p, c) in exp) {
                p.resurrect(c)
            }
        }
    }

    @JvmField
    val ATOMIC = GregChessModule.register("atomic", DetEndReason(EndReason.Type.NORMAL))

    override fun start(game: ChessGame) {
        game.requireComponent<ExplosionManager>()
    }

    private fun nextToKing(side: Side, pos: Pos, board: Chessboard): Boolean =
        pos in board.kingOf(side)?.pos?.neighbours().orEmpty()

    private fun kingHug(board: Chessboard): Boolean {
        val wk = board.kingOf(white)?.pos
        return wk != null && nextToKing(black, wk, board)
    }

    private fun pinningMoves(by: Side, pos: Square) =
        if (kingHug(pos.board)) emptyList() else Normal.pinningMoves(by, pos)

    private fun checkingMoves(by: Side, pos: Square) =
        if (nextToKing(by, pos.pos, pos.board)) emptyList() else Normal.checkingMoves(by, pos)

    override fun finishMove(move: Move, game: ChessGame) {
        if (move.getTrait<CaptureTrait>()?.captured != null)
            move.getTrait<TargetTrait>()?.target?.let { game.getComponent<ExplosionManager>()?.explode(it) }
    }

    override fun getLegality(move: Move, game: ChessGame): MoveLegality = with(move) {

        if (!Normal.isValid(this, game))
            return MoveLegality.INVALID
        val captured = getTrait<CaptureTrait>()?.let { game.board[it.capture]?.piece }
        if (piece.type == PieceType.KING) {
            if (captured != null)
                return MoveLegality.SPECIAL

            if (passedThrough.mapNotNull { game.board[it] }.all {
                    checkingMoves(!piece.side, it).isEmpty()
                }) MoveLegality.LEGAL else MoveLegality.IN_CHECK
        }

        val myKing = game.board.kingOf(piece.side) ?: return MoveLegality.IN_CHECK

        if (captured != null)
            if (myKing.pos in captured.pos.neighbours())
                return MoveLegality.SPECIAL

        val checks = checkingMoves(!piece.side, myKing.square)
        val capture = getTrait<CaptureTrait>()?.capture
        if (checks.any { ch -> capture != ch.piece.pos && startBlocking.none { it in ch.neededEmpty } })
            return MoveLegality.IN_CHECK
        val pins = pinningMoves(!piece.side, myKing.square)
        if (pins.any { pin ->
                capture != pin.piece.pos &&
                        pin.neededEmpty.filter { game.board[it]?.piece != null }
                            .all { it in stopBlocking } && startBlocking.none { it in pin.neededEmpty }
            })
            return MoveLegality.PINNED
        return MoveLegality.LEGAL
    }

    override fun isInCheck(king: BoardPiece): Boolean = checkingMoves(!king.side, king.square).isNotEmpty()

    override fun checkForGameEnd(game: ChessGame) {
        if (game.board.kingOf(!game.currentTurn) == null)
            game.stop(game.currentTurn.wonBy(ATOMIC))
        Normal.checkForGameEnd(game)
    }

    override fun undoLastMove(move: Move, game: ChessGame) {
        if (move.getTrait<CaptureTrait>()?.captured != null)
            game.requireComponent<ExplosionManager>().reverseExplosion()
        move.undo(game)
    }

    override val requiredComponents: Set<KClass<out Component>> = setOf(ExplosionManager::class)
}