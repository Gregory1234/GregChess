package gregc.gregchess.chess

import gregc.gregchess.ConfigManager
import gregc.gregchess.chess.component.Chessboard
import gregc.gregchess.glog

abstract class ChessVariant(val name: String) {
    companion object {
        private val normal = Normal

        private val variants = mutableMapOf<String, ChessVariant>()

        init {
            this += Normal
            this += ThreeChecks
            this += KingOfTheHill
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

    abstract fun start(game: ChessGame)
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

    object Normal : ChessVariant("Normal") {
        override fun start(game: ChessGame) {
        }

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

            val myKing =
                game.tryOrStopNull(
                    game.board.piecesOf(piece.side).find { it.type == ChessType.KING })
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

    object ThreeChecks : ChessVariant("ThreeChecks") {
        class CheckCounter(private val game: ChessGame) : ChessGame.Component {
            private var whiteChecks = 0
            private var blackChecks = 0

            override fun start() {
                game.scoreboard += object :
                    PlayerProperty(ConfigManager.getString("Component.CheckCounter.CheckCounter")) {
                    override fun invoke(s: ChessSide): String = when (s) {
                        ChessSide.WHITE -> whiteChecks
                        ChessSide.BLACK -> blackChecks
                    }.toString()
                }
            }

            operator fun plusAssign(side: ChessSide) {
                when (side) {
                    ChessSide.WHITE -> {
                        if (++whiteChecks >= 3)
                            game.stop(ThreeChecksEndReason(ChessSide.BLACK))
                    }
                    ChessSide.BLACK -> {
                        if (++blackChecks >= 3)
                            game.stop(ThreeChecksEndReason(ChessSide.WHITE))
                    }
                }
            }
        }

        class ThreeChecksEndReason(winner: ChessSide) :
            ChessGame.EndReason("Chess.EndReason.ThreeChecks", "normal", winner)

        override fun start(game: ChessGame) {
            game.registerComponent(CheckCounter(game))
        }

        override fun finishMove(move: ChessMove) {
            val game = move.origin.game
            val data = move.execute()
            game.board.lastMove?.clear()
            game.board.lastMove = data
            game.board.lastMove?.render()
            glog.low("Finished move", data)
            val enemyKing = game.tryOrStopNull(
                game.board.piecesOf(!game.currentTurn).find { it.type == ChessType.KING })
            if (isInCheck(enemyKing))
                game.getComponent(CheckCounter::class)!! += !game.currentTurn
            game.nextTurn()
        }

        override fun isLegal(move: ChessMove) = Normal.isLegal(move)

        override fun isInCheck(king: ChessPiece) = Normal.isInCheck(king)

    }

    object KingOfTheHill: ChessVariant("KingOfTheHill") {

        class KingOfTheHillEndReason(winner: ChessSide) :
            ChessGame.EndReason("Chess.EndReason.KingOfTheHill", "normal", winner)

        override fun start(game: ChessGame) {
        }

        override fun finishMove(move: ChessMove) {
            val game = move.origin.game
            val data = move.execute()
            game.board.lastMove?.clear()
            game.board.lastMove = data
            game.board.lastMove?.render()
            glog.low("Finished move", data)
            if(move.piece.type == ChessType.KING && listOf(move.target.pos.file, move.target.pos.rank).all {it in (3..4)})
                game.stop(KingOfTheHillEndReason(move.piece.side))
            else
                game.nextTurn()
        }

        override fun isLegal(move: ChessMove) = Normal.isLegal(move)

        override fun isInCheck(king: ChessPiece) = Normal.isInCheck(king)
    }
}