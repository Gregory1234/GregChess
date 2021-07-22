package gregc.gregchess.chess.variant

import gregc.gregchess.*
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Chessboard
import gregc.gregchess.chess.component.Component
import kotlin.reflect.KClass

open class ChessVariant(val id: Identifier) {
    @Suppress("unused")
    companion object {

        private val variants = mutableMapOf<Identifier, ChessVariant>()

        operator fun plusAssign(v: ChessVariant) {
            variants[v.id] = v
        }

        @JvmField
        val NORMAL = Normal
        @JvmField
        val THREE_CHECKS = ThreeChecks
        @JvmField
        val KING_OF_THE_HILL = KingOfTheHill
        @JvmField
        val ATOMIC = AtomicChess
        @JvmField
        val ANTICHESS = Antichess
        @JvmField
        val HORDE = HordeChess

        operator fun get(id: Identifier?) = when (id) {
            null -> NORMAL
            else -> variants[id] ?: run {
                glog.warn("Variant $id not found, defaulted to Normal")
                NORMAL
            }
        }
    }

    enum class MoveLegality(val prettyName: String) {
        INVALID("Invalid moves"),
        IN_CHECK("Moves blocked because of checks"),
        PINNED("Moves blocked by pins"),
        SPECIAL("Moves blocked for other reasons"),
        LEGAL("Legal moves")
    }

    init {
        @Suppress("LeakingThis")
        ChessVariant += this
    }

    open fun start(game: ChessGame) {}
    open fun chessboardSetup(board: Chessboard) {}
    open fun finishMove(move: MoveCandidate) {}
    open fun getLegality(move: MoveCandidate): MoveLegality = Normal.getLegality(move)
    open fun isInCheck(king: BoardPiece): Boolean = Normal.isInCheck(king)
    open fun checkForGameEnd(game: ChessGame) = Normal.checkForGameEnd(game)
    open fun timeout(game: ChessGame, side: Side) = Normal.timeout(game, side)
    open fun undoLastMove(move: MoveData) = Normal.undoLastMove(move)
    open fun getPieceMoves(piece: BoardPiece): List<MoveCandidate> = Normal.getPieceMoves(piece)

    open fun isInCheck(game: ChessGame, side: Side): Boolean {
        val king = game.board.kingOf(side)
        return king != null && isInCheck(king)
    }

    fun isLegal(move: MoveCandidate) = getLegality(move) == MoveLegality.LEGAL

    open fun genFEN(chess960: Boolean): FEN = Normal.genFEN(chess960)

    open val requiredComponents: Collection<KClass<out Component.Settings<*>>>
        get() = Normal.requiredComponents

    open val optionalComponents: Collection<KClass<out Component.Settings<*>>>
        get() = Normal.optionalComponents

    protected fun allMoves(side: Side, board: Chessboard) = board.piecesOf(side).flatMap { board.getMoves(it.pos) }

    object Normal : ChessVariant("normal".asIdent()) {

        fun pinningMoves(by: Side, pos: Square) =
            allMoves(by, pos.board).filter { it.control == pos }.filter { m -> m.blocks.count { it !in m.help } == 1 }

        fun checkingMoves(by: Side, pos: Square) =
            allMoves(by, pos.board).filter { it.control == pos }.filter { m ->
                m.needed.mapNotNull { m.board[it]?.piece }
                    .all { it.side == !m.piece.side && it.type == PieceType.KING || it in m.help }
            }

        fun isValid(move: MoveCandidate): Boolean = with(move) {

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

        override fun getLegality(move: MoveCandidate): MoveLegality = with(move) {
            if (!isValid(move))
                return MoveLegality.INVALID

            if (piece.type == PieceType.KING) {
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

        override fun isInCheck(king: BoardPiece): Boolean =
            checkingMoves(!king.side, king.square).isNotEmpty()

        override fun checkForGameEnd(game: ChessGame) = with(game.board) {
            if (piecesOf(!game.currentTurn).all { getMoves(it.pos).none(game.variant::isLegal) }) {
                if (isInCheck(game, !game.currentTurn))
                    game.stop(game.currentTurn.wonBy(EndReason.CHECKMATE))
                else
                    game.stop(drawBy(EndReason.STALEMATE))
            }
            checkForRepetition()
            checkForFiftyMoveRule()
            val whitePieces = piecesOf(Side.WHITE)
            val blackPieces = piecesOf(Side.BLACK)
            if (whitePieces.size == 1 && blackPieces.size == 1)
                game.stop(drawBy(EndReason.INSUFFICIENT_MATERIAL))
            if (whitePieces.size == 2 && whitePieces.any { it.type.minor } && blackPieces.size == 1)
                game.stop(drawBy(EndReason.INSUFFICIENT_MATERIAL))
            if (blackPieces.size == 2 && blackPieces.any { it.type.minor } && whitePieces.size == 1)
                game.stop(drawBy(EndReason.INSUFFICIENT_MATERIAL))
        }

        override fun timeout(game: ChessGame, side: Side) {
            if (game.board.piecesOf(!side).size == 1)
                game.stop(drawBy(EndReason.DRAW_TIMEOUT))
            else
                game.stop(side.lostBy(EndReason.TIMEOUT))
        }

        override fun undoLastMove(move: MoveData) = move.undo()

        override fun getPieceMoves(piece: BoardPiece): List<MoveCandidate> = piece.type.moveScheme(piece)

        override fun genFEN(chess960: Boolean) = if (!chess960) FEN() else FEN.generateChess960()

        override val requiredComponents: Collection<KClass<out Component.Settings<*>>>
            get() = emptyList()

        override val optionalComponents: Collection<KClass<out Component.Settings<*>>>
            get() = emptyList()
    }

}