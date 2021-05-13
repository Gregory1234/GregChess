package gregc.gregchess.chess.variant

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.*
import gregc.gregchess.glog

abstract class ChessVariant(val name: String) {

    companion object {
        private val normal = Normal

        private val variants = mutableMapOf<String, ChessVariant>()

        init {
            this += Normal
            this += ThreeChecks
            this += KingOfTheHill
            this += AtomicChess
            this += Antichess
            this += HordeChess
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

    enum class MoveLegality {
        INVALID, IN_CHECK, PINNED, SPECIAL, LEGAL
    }

    open fun start(game: ChessGame) {}
    open fun chessboardSetup(board: Chessboard) {}
    open fun finishMove(move: MoveCandidate) {}
    open fun getLegality(move: MoveCandidate): MoveLegality = Normal.getLegality(move)
    open fun isInCheck(king: ChessPiece): Boolean = Normal.isInCheck(king)
    open fun checkForGameEnd(game: ChessGame) = Normal.checkForGameEnd(game)
    open fun timeout(game: ChessGame, side: ChessSide) = Normal.timeout(game, side)
    open fun undoLastMove(move: MoveData) = Normal.undoLastMove(move)

    open fun isInCheck(game: ChessGame, side: ChessSide): Boolean {
        val king = game.board.kingOf(side)
        return king != null && isInCheck(king)
    }

    fun isLegal(move: MoveCandidate) = getLegality(move) == MoveLegality.LEGAL

    open fun genFEN(chess960: Boolean): FEN = Normal.genFEN(chess960)
    open val promotions: Collection<ChessType>
        get() = Normal.promotions

    protected fun allMoves(side: ChessSide, board: Chessboard) =
        board.piecesOf(side).flatMap { board.getMoves(it.pos) }

    object Normal : ChessVariant("Normal") {

        fun pinningMoves(by: ChessSide, pos: ChessSquare) =
            allMoves(by, pos.board).filter { it.control == pos }
                .filter { m -> m.blocks.count { it !in m.help } == 1 }

        fun checkingMoves(by: ChessSide, pos: ChessSquare) =
            allMoves(by, pos.board).filter { it.control == pos }.filter { m ->
                m.needed.mapNotNull { m.board[it]?.piece }
                    .all { it.side == !m.piece.side && it.type == ChessType.KING || it in m.help }
            }

        fun isValid(move: MoveCandidate): Boolean = move.run {

            if (needed.any { p -> board[p].let { it?.piece != null && it.piece !in help } })
                return false

            if (target.piece != null && control != target && target.piece !in help)
                return false

            if (captured == null && mustCapture)
                return false

            if (captured?.side == piece.side)
                return false

            return true
        }

        override fun getLegality(move: MoveCandidate): MoveLegality = move.run {
            if (!isValid(move))
                return MoveLegality.INVALID

            if (piece.type == ChessType.KING) {
                return if ((pass + target.pos).mapNotNull { game.board[it] }.all {
                        checkingMoves(!piece.side, it).isEmpty()
                    }) MoveLegality.LEGAL else MoveLegality.IN_CHECK
            }

            val myKing = game.tryOrStopNull(game.board.kingOf(piece.side))
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

}