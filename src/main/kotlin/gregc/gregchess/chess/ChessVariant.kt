package gregc.gregchess.chess

import gregc.gregchess.chess.component.Chessboard
import gregc.gregchess.glog

abstract class ChessVariant(val name: String) {
    companion object {
        private val normal = Normal

        private val variants = mutableMapOf<String, ChessVariant>()

        init {
            this += Normal
            /*this += ThreeChecks
            this += KingOfTheHill
            this += Atomic*/
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
    abstract fun finishMove(move: MoveCandidate)
    abstract fun isLegal(move: MoveCandidate): Boolean
    abstract fun isInCheck(king: ChessPiece): Boolean
    abstract fun checkForGameEnd(game: ChessGame)

    protected fun allMoves(side: ChessSide, board: Chessboard) =
        board.piecesOf(side).flatMap { board.getMoves(it.pos) }

    object Normal : ChessVariant("Normal") {

        private fun pinningMoves(by: ChessSide, pos: ChessSquare) =
            allMoves(by, pos.board).filter { it.control == pos }
                .filter { m -> m.pass.count { pos.board[it] != null } == 1 }

        private fun checkingPotentialMoves(by: ChessSide, pos: ChessSquare) =
            allMoves(by, pos.board).filter { it.target == pos }
                .filter { it.control != null }
                .filter { m -> m.pass.all { pos.board[it] == null } }

        private fun checkingMoves(by: ChessSide, pos: ChessSquare) =
            allMoves(by, pos.board).filter { it.control == pos }
                .filter { m -> m.pass.all { pos.board[it] == null } }

        override fun start(game: ChessGame) {
        }

        override fun finishMove(move: MoveCandidate) {
            val game = move.origin.game
            val data = move.execute()
            game.board.lastMove?.clear()
            game.board.lastMove = data
            game.board.lastMove?.render()
            glog.low("Finished move", data)
            game.nextTurn()
        }

        private fun isValid(move: MoveCandidate): Boolean = move.run {

            if (needed.any { p -> origin.board[p].let { it != null && it !in help }})
                return false

            if (target.piece != null && control != target && target.piece !in help)
                return false

            if (control?.piece == null && mustCapture)
                return false

            if (control?.piece?.side == piece.side)
                return false

            return true
        }

        override fun isLegal(move: MoveCandidate): Boolean = move.run {
            val game = origin.game

            if (!isValid(move))
                return false

            if (piece.type == ChessType.KING) {
                return (pass + target.pos).mapNotNull { game.board.getSquare(it) }.all {
                    checkingPotentialMoves(!piece.side, it).isEmpty()
                }
            }

            val myKing =
                game.tryOrStopNull(
                    game.board.piecesOf(piece.side).find { it.type == ChessType.KING })
            val checks = checkingMoves(!piece.side, myKing.square)
            if (checks.any { target.pos !in it.pass && target != it.origin })
                return false
            val pins = pinningMoves(!piece.side, myKing.square).filter { origin.pos in it.pass }
            if (pins.any { target.pos !in it.pass && target != it.origin })
                return false
            return true
        }

        override fun isInCheck(king: ChessPiece): Boolean =
            checkingMoves(!king.side, king.square).isNotEmpty()

        override fun checkForGameEnd(game: ChessGame) {
            if (game.board.piecesOf(!game.currentTurn)
                    .all { game.board.getMoves(it.pos).none(game.variant::isLegal) }
            ) game.stop(ChessGame.EndReason.Checkmate(game.currentTurn))
            game.board.checkForRepetition()
            game.board.checkForFiftyMoveRule()
            val whitePieces = game.board.piecesOf(ChessSide.WHITE)
            val blackPieces = game.board.piecesOf(ChessSide.BLACK)
            if (whitePieces.size == 1 && blackPieces.size == 1)
                game.stop(ChessGame.EndReason.InsufficientMaterial())
            if (whitePieces.size == 2 && whitePieces.any { it.type.minor } && blackPieces.size == 1)
                game.stop(ChessGame.EndReason.InsufficientMaterial())
            if (blackPieces.size == 2 && blackPieces.any { it.type.minor } && whitePieces.size == 1)
                game.stop(ChessGame.EndReason.InsufficientMaterial())
        }
    }

    /*object ThreeChecks : ChessVariant("ThreeChecks") {
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

    object KingOfTheHill : ChessVariant("KingOfTheHill") {

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
            if (move.piece.type == ChessType.KING && listOf(
                    move.target.pos.file,
                    move.target.pos.rank
                ).all { it in (3..4) }
            )
                game.stop(KingOfTheHillEndReason(move.piece.side))
            else
                game.nextTurn()
        }

        override fun isLegal(move: ChessMove) = Normal.isLegal(move)

        override fun isInCheck(king: ChessPiece) = Normal.isInCheck(king)
    }

    object Atomic : ChessVariant("Atomic") {
        class AtomicEndReason(winner: ChessSide) :
            ChessGame.EndReason("Chess.EndReason.Atomic", "normal", winner)

        override fun start(game: ChessGame) {
        }

        override fun finishMove(move: ChessMove) {
            val game = move.origin.game
            val data = move.execute()
            game.board.lastMove?.clear()
            game.board.lastMove = data
            game.board.lastMove?.render()
            glog.low("Finished move", data)
            if (move is ChessMove.Attack) {
                move.target.pos.neighbours().forEach { pos ->
                    game.board[pos]?.let {
                        if (it.type == ChessType.KING)
                            game.stop(AtomicEndReason(move.piece.side))
                        if (it.type != ChessType.PAWN)
                            it.capture(move.piece.side)
                    }
                }
                move.piece.capture(move.piece.side)
            }
            game.nextTurn()
        }

        override fun isLegal(move: ChessMove): Boolean = move.run {
            val game = origin.game

            if (!isValid) return false
            if (piece.type == ChessType.KING) {
                val checks =
                    zeroIfTogether(!piece.side, target, checkingPotentialMoves(!piece.side, target))
                return move !is ChessMove.Attack && checks.isEmpty()
            }

            if (move is ChessMove.Attack && target.pos.neighbours().mapNotNull { game.board[it] }
                    .any { it.type == ChessType.KING && it.side == piece.side })
                return false

            val myKing =
                game.tryOrStopNull(
                    game.board.piecesOf(piece.side).find { it.type == ChessType.KING })
            val checks = zeroIfTogether(
                !piece.side,
                myKing.square,
                checkingMoves(!piece.side, myKing.square)
            )
            if (checks.any { target.pos !in it.potentialBlocks && target != it.origin })
                return false
            val pins = zeroIfTogether(
                !piece.side,
                myKing.square,
                pinningMoves(
                    !piece.side,
                    myKing.square
                ).filter { it.actualBlocks[0] == origin.pos })
            if (pins.any { target.pos !in it.potentialBlocks && target != it.origin })
                return false
            return true
        }

        private fun <T> zeroIfTogether(by: ChessSide, pos: ChessSquare, list: List<T>) =
            if (pos.pos.neighbours().mapNotNull { pos.game.board[it] }
                    .any { it.type == ChessType.KING && it.side == by }) emptyList()
            else list

        override fun isInCheck(king: ChessPiece) =
            zeroIfTogether(
                !king.side,
                king.square,
                checkingMoves(!king.side, king.square)
            ).isNotEmpty()
    }*/
}