package gregc.gregchess.chess.variant

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Chessboard
import gregc.gregchess.chess.component.Component
import gregc.gregchess.chess.move.*
import gregc.gregchess.chess.piece.*
import gregc.gregchess.registry.*
import gregc.gregchess.rotationsOf
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable(with = ChessVariant.Serializer::class)
open class ChessVariant : NameRegistered {

    object Serializer : NameRegisteredSerializer<ChessVariant>("ChessVariant", RegistryType.VARIANT)

    final override val key get() = RegistryType.VARIANT[this]

    final override fun toString(): String = "$key@${hashCode().toString(16)}"

    enum class MoveLegality(val prettyName: String) {
        INVALID("Invalid moves"),
        IN_CHECK("Moves blocked because of checks"),
        PINNED("Moves blocked by pins"),
        SPECIAL("Moves blocked for other reasons"),
        LEGAL("Legal moves")
    }

    open fun chessboardSetup(board: Chessboard) {}

    open fun getLegality(move: Move, game: ChessGame): MoveLegality = with(move) {
        if (!Normal.isValid(move, game))
            return MoveLegality.INVALID

        if (piece.type == PieceType.KING) {
            return if (passedThrough.all { Normal.checkingMoves(!piece.color, it, game.board).isEmpty() })
                MoveLegality.LEGAL
            else
                MoveLegality.IN_CHECK
        }

        val myKing = game.tryOrStopNull(game.board.kingOf(piece.color))
        val checks = Normal.checkingMoves(!piece.color, myKing.pos, game.board)
        val capture = getTrait<CaptureTrait>()?.capture
        if (checks.any { ch -> capture != ch.piece.pos && startBlocking.none { it in ch.neededEmpty } })
            return MoveLegality.IN_CHECK
        val pins = Normal.pinningMoves(!piece.color, myKing.pos, game.board)
        if (pins.any { pin ->
                capture != pin.piece.pos &&
                        pin.neededEmpty.filter { game.board[it]?.piece != null }
                            .all { it in stopBlocking } && startBlocking.none { it in pin.neededEmpty }
            })
            return MoveLegality.PINNED
        return MoveLegality.LEGAL
    }

    open fun isInCheck(king: BoardPiece, board: Chessboard): Boolean =
        Normal.checkingMoves(!king.color, king.pos, board).isNotEmpty()

    open fun checkForGameEnd(game: ChessGame) = with(game.board) {
        if (piecesOf(!game.currentTurn).all { it.getMoves(this).none { m -> game.variant.isLegal(m, game) } }) {
            if (isInCheck(game, !game.currentTurn))
                game.stop(game.currentTurn.wonBy(EndReason.CHECKMATE))
            else
                game.stop(drawBy(EndReason.STALEMATE))
        }
        checkForRepetition()
        checkForFiftyMoveRule()
        val whitePieces = piecesOf(Color.WHITE)
        val blackPieces = piecesOf(Color.BLACK)
        if (whitePieces.size == 1 && blackPieces.size == 1)
            game.stop(drawBy(EndReason.INSUFFICIENT_MATERIAL))
        val minorPieces = listOf(PieceType.KNIGHT, PieceType.BISHOP)
        if (whitePieces.size == 2 && whitePieces.any { it.type in minorPieces } && blackPieces.size == 1)
            game.stop(drawBy(EndReason.INSUFFICIENT_MATERIAL))
        if (blackPieces.size == 2 && blackPieces.any { it.type in minorPieces } && whitePieces.size == 1)
            game.stop(drawBy(EndReason.INSUFFICIENT_MATERIAL))
    }

    open fun timeout(game: ChessGame, color: Color) {
        if (game.board.piecesOf(!color).size == 1)
            game.stop(drawBy(EndReason.DRAW_TIMEOUT))
        else
            game.stop(color.lostBy(EndReason.TIMEOUT))
    }

    open fun getPieceMoves(piece: BoardPiece, board: Chessboard): List<Move> = when(piece.type) {
        PieceType.KING -> KingMovement
        PieceType.QUEEN -> RayMovement(rotationsOf(1, 1) + rotationsOf(1, 0))
        PieceType.ROOK -> RayMovement(rotationsOf(1, 0))
        PieceType.BISHOP -> RayMovement(rotationsOf(1, 1))
        PieceType.KNIGHT -> JumpMovement(rotationsOf(2, 1))
        PieceType.PAWN -> PromotionMovement(PawnMovement(), with(PieceType) { listOf(QUEEN, ROOK, BISHOP, KNIGHT) })
        else -> throw IllegalArgumentException(piece.type.toString())
    }.generate(piece, board)

    open fun isInCheck(game: ChessGame, color: Color): Boolean {
        val king = game.board.kingOf(color)
        return king != null && isInCheck(king, game.board)
    }

    fun isLegal(move: Move, game: ChessGame) = getLegality(move, game) == MoveLegality.LEGAL

    open fun startingPieceHasMoved(fen: FEN, pos: Pos, piece: Piece): Boolean = when(piece.type) {
        PieceType.PAWN -> when (piece.color) {
            Color.WHITE -> pos.rank != 1
            Color.BLACK -> pos.rank != 6
        }
        PieceType.ROOK -> pos.file !in fen.castlingRights[piece.color]
        else -> false
    }

    open fun genFEN(chess960: Boolean): FEN = if (!chess960) FEN() else FEN.generateChess960()

    open val pieceTypes: Collection<PieceType>
        get() = PieceType.run { listOf(KING, QUEEN, ROOK, BISHOP, KNIGHT, PAWN) }

    open val requiredComponents: Set<KClass<out Component>> get() = emptySet()

    open val optionalComponents: Set<KClass<out Component>> get() = emptySet()

    protected fun allMoves(color: Color, board: Chessboard) = board.piecesOf(color).flatMap { it.getMoves(board) }

    object Normal : ChessVariant() {

        fun pinningMoves(by: Color, pos: Pos, board: Chessboard) =
            allMoves(by, board).filter { it.getTrait<CaptureTrait>()?.capture == pos }.filter { m ->
                !m.flagsNeeded.any { (p, f) -> board[p].let { s -> s?.flags?.any { it.type == f && it.active } == false } } &&
                        m.neededEmpty.any { board[it]?.piece != null }
            }

        fun checkingMoves(by: Color, pos: Pos, board: Chessboard) =
            allMoves(by, board).filter { it.getTrait<CaptureTrait>()?.capture == pos }.filter { m ->
                !m.flagsNeeded.any { (p, f) -> board[p].let { s -> s?.flags?.any { it.type == f && it.active } == false } } &&
                        m.neededEmpty.mapNotNull { board[it]?.piece }
                            .all { it.color == !m.piece.color && it.type == PieceType.KING }
            }

        fun isValid(move: Move, game: ChessGame): Boolean = with(move) {
            val board = game.board
            if (flagsNeeded.any { (p, f) -> board[p].let { s -> s?.flags?.any { it.type == f && it.active } == false } })
                return false

            if (neededEmpty.any { p -> board[p]?.piece != null })
                return false

            getTrait<CaptureTrait>()?.let {
                if (it.hasToCapture && board[it.capture]?.piece == null)
                    return false
                if (board[it.capture]?.piece?.color == piece.color)
                    return false
            }

            return true
        }

    }

}