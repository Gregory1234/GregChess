package gregc.gregchess.chess.component

import gregc.gregchess.chess.*
import gregc.gregchess.glog
import java.lang.NullPointerException

abstract class ChessVariant(val name: String) {
    companion object {
        private val normal = Normal()

        private val variants = mutableMapOf<String, ChessVariant>()

        init {
            this += Normal()
        }

        operator fun get(name: String?) = when (name) {
            null -> normal
            else -> variants[name] ?: run {
                glog.warn("Variant $name not found, defaulted to Normal")
                normal
            }
        }

        operator fun plusAssign(variant: ChessVariant) {
            variants[variant.name] = variant
        }
    }

    abstract fun finishMove(move: ChessMove)
    abstract fun isLegal(move: ChessMove): Boolean
    abstract fun isInCheck(king: ChessPiece): Boolean

    private fun allMoves(side: ChessSide, board: Chessboard) =
        board.piecesOf(side).flatMap { board.getMoves(it.pos) }

    protected fun pinningMoves(by: ChessSide, pos: ChessSquare) =
        allMoves(by, pos.board).mapNotNull { it as? ChessMove.Attack }
            .filter { it.target == pos && !it.defensive && it.actualBlocks.size == 1 }

    protected fun checkingPotentialMoves(by: ChessSide, pos: ChessSquare) =
        allMoves(by, pos.board).filter { it.target == pos }
            .filter { it.canAttack }

    protected fun checkingMoves(by: ChessSide, pos: ChessSquare) =
        allMoves(by, pos.board).mapNotNull { it as? ChessMove.Attack }
            .filter { it.target == pos && it.isValid }

    class Normal : ChessVariant("Normal") {
        override fun finishMove(move: ChessMove) {
            val game = move.origin.game
            val data = move.execute()
            game.board.lastMove?.clear()
            game.board.lastMove = data
            game.board.lastMove?.render()
            glog.low("Finished move", data)
            game.nextTurn()
        }

        override fun isLegal(move: ChessMove): Boolean = move.run {
            if (!isValid) return false
            if (piece.type == ChessType.KING) {
                val checks = checkingPotentialMoves(!piece.side, target)
                return checks.isEmpty()
            }
            val game = move.origin.game

            val myKing = try {
                game.board.piecesOf(piece.side).find { it.type == ChessType.KING }!!
            } catch (e: NullPointerException) {
                game.stop(ChessGame.EndReason.Error(e))
                throw e
            }
            val checks = checkingMoves(!piece.side, myKing.square)
            if (checks.any { target.pos !in it.potentialBlocks && target != it.origin })
                return false
            val pins =
                pinningMoves(!piece.side, myKing.square).filter { it.actualBlocks[0] == origin.pos }
            if (pins.any { target.pos !in it.potentialBlocks && target != it.origin })
                return false
            return true
        }

        override fun isInCheck(king: ChessPiece): Boolean =
            checkingMoves(!king.side, king.square).isNotEmpty()
    }
}