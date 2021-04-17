package gregc.gregchess.chess

import gregc.gregchess.ConfigManager
import gregc.gregchess.chess.component.Chessboard
import gregc.gregchess.doIn
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
            this += Antichess
            this += Horde
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
    abstract fun timeout(game: ChessGame, side: ChessSide)
    abstract fun undoLastMove(move: MoveData)

    open fun isInCheck(game: ChessGame, side: ChessSide): Boolean {
        val king = game.board.kingOf(side)
        return king != null && isInCheck(king)
    }

    abstract fun genFEN(chess960: Boolean): FEN
    abstract val promotions: Collection<ChessType>

    protected fun allMoves(side: ChessSide, board: Chessboard) =
        board.piecesOf(side).flatMap { board.getMoves(it.pos) }

    object Normal : ChessVariant("Normal") {

        fun pinningMoves(by: ChessSide, pos: ChessSquare) =
            allMoves(by, pos.board).filter { it.control == pos }
                .filter { m -> m.pass.count { pos.board[it]?.piece != null && pos.board[it]?.piece !in m.help } == 1 }

        fun checkingMoves(by: ChessSide, pos: ChessSquare) =
            allMoves(by, pos.board).filter { it.control == pos }.filter { m ->
                m.needed.mapNotNull { m.board[it]?.piece }
                    .all { it.side == !m.piece.side && it.type == ChessType.KING && it in m.help }
            }

        override fun start(game: ChessGame) {
        }

        override fun finishMove(move: MoveCandidate) = move.run {
            val data = execute()
            board.lastMove?.clear()
            board.lastMove = data
            board.lastMove?.render()
            glog.low("Finished move", data)
            game.nextTurn()
        }

        fun isValid(move: MoveCandidate): Boolean = move.run {

            if (needed.any { p -> board[p].let { it?.piece != null && it.piece !in help } })
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
            ) {
                if (isInCheck(game, !game.currentTurn))
                    game.stop(ChessGame.EndReason.Checkmate(game.currentTurn))
                else
                    game.stop(ChessGame.EndReason.Stalemate())
            }
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

        override fun timeout(game: ChessGame, side: ChessSide) {
            if (game.board.piecesOf(!side).size == 1)
                game.stop(ChessGame.EndReason.DrawTimeout())
            else
                game.stop(ChessGame.EndReason.Timeout(!side))
        }

        override fun undoLastMove(move: MoveData) = move.undo()

        override val promotions: Collection<ChessType>
            get() = listOf(ChessType.QUEEN, ChessType.ROOK, ChessType.BISHOP, ChessType.KNIGHT)

        override fun genFEN(chess960: Boolean) = if (!chess960) FEN() else FEN.generateChess960()
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

        override fun timeout(game: ChessGame, side: ChessSide) = Normal.timeout(game, side)

        override fun checkForGameEnd(game: ChessGame) {
            game.getComponent(CheckCounter::class)?.checkForGameEnd()
            Normal.checkForGameEnd(game)
        }

        override fun undoLastMove(move: MoveData) = Normal.undoLastMove(move)

        override val promotions: Collection<ChessType>
            get() = Normal.promotions

        override fun genFEN(chess960: Boolean) = Normal.genFEN(chess960)
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

        override fun timeout(game: ChessGame, side: ChessSide) = Normal.timeout(game, side)

        override fun checkForGameEnd(game: ChessGame) {
            game.board.pieces.filter { it.type == ChessType.KING }.forEach {
                if (it.pos.file in (3..4) && it.pos.rank in (3..4))
                    game.stop(KingOfTheHillEndReason(it.side))
            }
            Normal.checkForGameEnd(game)
        }

        override fun undoLastMove(move: MoveData) = Normal.undoLastMove(move)

        override val promotions: Collection<ChessType>
            get() = Normal.promotions

        override fun genFEN(chess960: Boolean) = Normal.genFEN(chess960)
    }

    object Atomic : ChessVariant("Atomic") {
        class ExplosionManager(private val game: ChessGame) : ChessGame.Component {
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
                game.board.renderer.getPieceLoc(pos).doIn(game.arena.world) { world, l ->
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

        override fun finishMove(move: MoveCandidate) = move.run {
            val data = execute()
            board.lastMove?.clear()
            board.lastMove = data
            board.lastMove?.render()
            if (data.captured)
                game.getComponent(ExplosionManager::class)?.explode(target.pos)
            glog.low("Finished move", data)
            game.nextTurn()
        }

        override fun isLegal(move: MoveCandidate): Boolean = move.run {
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

        override fun timeout(game: ChessGame, side: ChessSide) = Normal.timeout(game, side)

        override fun undoLastMove(move: MoveData) {
            if(move.captured)
                move.origin.game.getComponent(ExplosionManager::class)?.reverseExplosion()
            move.undo()
        }

        override val promotions: Collection<ChessType>
            get() = Normal.promotions

        override fun genFEN(chess960: Boolean) = Normal.genFEN(chess960)
    }

    object Antichess : ChessVariant("Antichess") {
        class Stalemate(winner: ChessSide) :
            ChessGame.EndReason("Chess.EndReason.Stalemate", "normal", winner)

        override fun start(game: ChessGame) {
        }

        override fun finishMove(move: MoveCandidate) = Normal.finishMove(move)

        override fun isLegal(move: MoveCandidate): Boolean {
            if (!Normal.isValid(move))
                return false
            if (move.piece.type == ChessType.KING && move.help.isNotEmpty())
                return false
            if (move.control?.piece != null)
                return true
            return move.board.piecesOf(move.piece.side).none { m ->
                m.square.bakedMoves.orEmpty().filter { Normal.isValid(it) }
                    .any { it.control?.piece != null }
            }
        }

        override fun isInCheck(king: ChessPiece) = false

        override fun isInCheck(game: ChessGame, side: ChessSide) = false

        override fun checkForGameEnd(game: ChessGame) {
            if (game.board.piecesOf(!game.currentTurn).isEmpty())
                game.stop(ChessGame.EndReason.AllPiecesLost(!game.currentTurn))
            if (game.board.piecesOf(!game.currentTurn)
                    .all { game.board.getMoves(it.pos).none(game.variant::isLegal) }
            ) {
                game.stop(Stalemate(!game.currentTurn))
            }
            game.board.checkForRepetition()
            game.board.checkForFiftyMoveRule()
        }

        override fun timeout(game: ChessGame, side: ChessSide) =
            game.stop(ChessGame.EndReason.Timeout(side))

        override fun undoLastMove(move: MoveData) = Normal.undoLastMove(move)

        override val promotions: Collection<ChessType>
            get() = Normal.promotions + ChessType.KING

        override fun genFEN(chess960: Boolean) = Normal.genFEN(chess960)
    }

    object Horde : ChessVariant("Horde") {
        override fun start(game: ChessGame) {
            game.board.piecesOf(ChessSide.WHITE).filter { it.pos.rank == 0 }.forEach {
                it.force(false)
            }
        }

        override fun finishMove(move: MoveCandidate) {
            Normal.finishMove(move)
            if (move.piece.type == ChessType.PAWN && move.target.pos.rank == 1) {
                move.piece.force(false)
            }
        }

        override fun isLegal(move: MoveCandidate): Boolean =
            if (move.piece.side == ChessSide.BLACK)
                Normal.isLegal(move)
            else
                Normal.isValid(move)

        override fun isInCheck(king: ChessPiece) =
            king.side == ChessSide.BLACK && Normal.isInCheck(king)

        override fun checkForGameEnd(game: ChessGame) {
            if (game.board.piecesOf(ChessSide.BLACK)
                    .all { game.board.getMoves(it.pos).none(game.variant::isLegal) }
            ) {
                if (isInCheck(game, ChessSide.BLACK))
                    game.stop(ChessGame.EndReason.Checkmate(ChessSide.WHITE))
                else
                    game.stop(ChessGame.EndReason.Stalemate())
            }
            if (game.board.piecesOf(ChessSide.WHITE).isEmpty())
                game.stop(ChessGame.EndReason.AllPiecesLost(ChessSide.BLACK))
            game.board.checkForRepetition()
            game.board.checkForFiftyMoveRule()
        }

        override fun timeout(game: ChessGame, side: ChessSide) =
            game.stop(ChessGame.EndReason.Timeout(side))

        override fun undoLastMove(move: MoveData) = Normal.undoLastMove(move)

        override val promotions: Collection<ChessType>
            get() = Normal.promotions

        override fun genFEN(chess960: Boolean): FEN {
            val base = Normal.genFEN(chess960)
            val state = base.boardState.split("/").dropLast(5)
                .joinToString("/") + "/1PP2PP1/PPPPPPPP/PPPPPPPP/PPPPPPPP/PPPPPPPP"
            return base.copy(boardState = state, castlingRightsBlack = emptyList())
        }
    }
}