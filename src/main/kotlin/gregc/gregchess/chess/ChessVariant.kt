package gregc.gregchess.chess

import gregc.gregchess.ConfigManager
import gregc.gregchess.chess.component.Chessboard
import gregc.gregchess.glog
import gregc.gregchess.star
import org.bukkit.Material

abstract class ChessVariant(val name: String) {
    companion object {
        private val normal = Normal

        private val variants = mutableMapOf<String, ChessVariant>()

        init {
            this += Normal
            this += ThreeChecks
            this += KingOfTheHill
            this += Atomic
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
    open fun isInCheck(game: ChessGame, side: ChessSide): Boolean {
        val king = game.board.kingOf(side)
        return king != null && isInCheck(king)
    }
    abstract fun checkForGameEnd(game: ChessGame)

    protected fun allMoves(side: ChessSide, board: Chessboard) =
        board.piecesOf(side).flatMap { board.getMoves(it.pos) }

    object Normal : ChessVariant("Normal") {

        fun pinningMoves(by: ChessSide, pos: ChessSquare) =
            allMoves(by, pos.board).filter { it.control == pos }
                .filter { m -> m.pass.count { pos.board[it] != null && pos.board[it]?.piece !in m.help } == 1 }

        fun checkingMoves(by: ChessSide, pos: ChessSquare) =
            allMoves(by, pos.board).filter { it.control == pos }.filter { m ->
                m.needed.none { p -> m.origin.board[p]?.piece.let { it != null && it !in m.help && !(it.side == !m.piece.side && it.type == ChessType.KING) } }
            }

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

        fun isValid(move: MoveCandidate): Boolean = move.run {

            if (needed.any { p -> origin.board[p].let { it != null && it.piece !in help } })
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
                return (pass + target.pos).mapNotNull { game.board[it] }.all {
                    checkingMoves(!piece.side, it).isEmpty()
                }
            }

            val myKing = game.tryOrStopNull(game.board.kingOf(piece.side))
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

            override fun endTurn() {
                if (game.variant.isInCheck(game, !game.currentTurn))
                    when (!game.currentTurn) {
                        ChessSide.WHITE -> {
                            whiteChecks++
                        }
                        ChessSide.BLACK -> {
                            blackChecks++
                        }
                    }
            }

            fun checkForGameEnd() {
                if (whiteChecks >= 3)
                    game.stop(ThreeChecksEndReason(ChessSide.BLACK))
                if (blackChecks >= 3)
                    game.stop(ThreeChecksEndReason(ChessSide.WHITE))
            }
        }

        class ThreeChecksEndReason(winner: ChessSide) :
            ChessGame.EndReason("Chess.EndReason.ThreeChecks", "normal", winner)

        override fun start(game: ChessGame) {
            game.registerComponent(CheckCounter(game))
        }

        override fun finishMove(move: MoveCandidate) = Normal.finishMove(move)

        override fun isLegal(move: MoveCandidate): Boolean = Normal.isLegal(move)

        override fun isInCheck(king: ChessPiece): Boolean = Normal.isInCheck(king)

        override fun checkForGameEnd(game: ChessGame) {
            game.getComponent(CheckCounter::class)?.checkForGameEnd()
            Normal.checkForGameEnd(game)
        }

    }

    object KingOfTheHill : ChessVariant("KingOfTheHill") {

        class KingOfTheHillEndReason(winner: ChessSide) :
            ChessGame.EndReason("Chess.EndReason.KingOfTheHill", "normal", winner)

        override fun start(game: ChessGame) {
            (3..4).star((3..4)) { x, y ->
                game.board[ChessPosition(x, y)]?.variantMarker = Material.PURPLE_CONCRETE
            }
        }

        override fun finishMove(move: MoveCandidate) = Normal.finishMove(move)

        override fun isLegal(move: MoveCandidate): Boolean = Normal.isLegal(move)

        override fun isInCheck(king: ChessPiece): Boolean = Normal.isInCheck(king)

        override fun checkForGameEnd(game: ChessGame) {
            game.board.pieces.filter { it.type == ChessType.KING }.forEach {
                if (it.pos.file in (3..4) && it.pos.rank in (3..4))
                    game.stop(KingOfTheHillEndReason(it.side))
            }
            Normal.checkForGameEnd(game)
        }
    }

    object Atomic : ChessVariant("Atomic") {
        class AtomicEndReason(winner: ChessSide) :
            ChessGame.EndReason("Chess.EndReason.Atomic", "normal", winner)

        override fun start(game: ChessGame) {
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
            val game = move.origin.game
            val attacking = move.control?.piece != null
            val data = move.execute()
            game.board.lastMove?.clear()
            game.board.lastMove = data
            game.board.lastMove?.render()
            if (attacking) {
                (-1..1).star((-1..1)) { x, y ->
                    game.board[move.target.pos + Pair(x, y)]?.piece?.let {
                        if (it.type != ChessType.PAWN || (x == 0 && y == 0))
                            it.capture(move.piece.side)
                    }
                }
                game.arena.world.createExplosion(
                    game.board.renderer.getPieceLoc(move.piece.pos).toLocation(game.arena.world),
                    4.0f, false, false
                )
            }
            glog.low("Finished move", data)
            game.nextTurn()
        }

        override fun isLegal(move: MoveCandidate): Boolean = move.run {
            val game = origin.game

            if (!Normal.isValid(move))
                return false

            if (piece.type == ChessType.KING) {
                if (move.control?.piece != null)
                    return false

                return (pass + target.pos).mapNotNull { game.board[it] }.all {
                    checkingMoves(!piece.side, it).isEmpty()
                }
            }

            val myKing = game.board.kingOf(piece.side) ?: return false

            if (move.control?.piece != null)
                if (myKing.pos in move.target.pos.neighbours())
                    return false

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
            if (game.board.kingOf(!game.currentTurn) == null)
                game.stop(AtomicEndReason(game.currentTurn))
            Normal.checkForGameEnd(game)
        }
    }
}