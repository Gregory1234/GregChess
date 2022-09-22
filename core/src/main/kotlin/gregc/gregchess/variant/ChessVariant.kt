package gregc.gregchess.variant

import gregc.gregchess.*
import gregc.gregchess.board.Chessboard
import gregc.gregchess.board.FEN
import gregc.gregchess.component.ComponentIdentifier
import gregc.gregchess.match.ChessMatch
import gregc.gregchess.match.PGN
import gregc.gregchess.move.*
import gregc.gregchess.move.connector.ChessboardView
import gregc.gregchess.move.trait.*
import gregc.gregchess.piece.*
import gregc.gregchess.registry.*
import gregc.gregchess.results.*
import kotlinx.serialization.Serializable

@Serializable(with = ChessVariant.Serializer::class)
open class ChessVariant : NameRegistered {

    object Serializer : NameRegisteredSerializer<ChessVariant>("ChessVariant", CoreRegistry.VARIANT)

    final override val key get() = CoreRegistry.VARIANT[this]

    final override fun toString(): String = CoreRegistry.VARIANT.simpleElementToString(this)

    enum class MoveLegality(val prettyName: String) {
        ERROR("Invalid chessboard state"),
        INVALID("Invalid moves"),
        IN_CHECK("Moves blocked because of checks"),
        PINNED("Moves blocked by pins"),
        SPECIAL("Moves blocked for other reasons"),
        LEGAL("Legal moves")
    }

    open fun getLegality(move: Move, board: ChessboardView): MoveLegality = with(move) {
        if (!Normal.isValid(move, board))
            return MoveLegality.INVALID

        if (main.type == PieceType.KING) {
            return if (passedThrough.all { Normal.checkingMoves(!main.color, it, board).isEmpty() })
                MoveLegality.LEGAL
            else
                MoveLegality.IN_CHECK
        }

        val myKing = board.kingOf(main.color) ?: return MoveLegality.ERROR
        val checks = Normal.checkingMoves(!main.color, myKing.pos, board)
        val capture = captureTrait?.capture
        if (checks.any { ch -> capture != ch.origin && startBlocking.none { it in ch.neededEmpty } })
            return MoveLegality.IN_CHECK

        val pins = Normal.pinningMoves(!main.color, myKing.pos, board)
        if (pins.any { pin -> isPinnedBy(pin, board) })
            return MoveLegality.PINNED

        return MoveLegality.LEGAL
    }

    fun isLegal(move: Move, board: ChessboardView) = getLegality(move, board) == MoveLegality.LEGAL

    open fun isInCheck(king: BoardPiece, board: ChessboardView): Boolean =
        Normal.checkingMoves(!king.color, king.pos, board).isNotEmpty()

    open fun isInCheck(board: ChessboardView, color: Color): Boolean {
        val king = board.kingOf(color)
        return king != null && isInCheck(king, board)
    }

    open fun checkForMatchEnd(match: ChessMatch) = with(match.board) { // TODO: return EndReason instead of stopping directly
        if (piecesOf(!match.currentColor).all { it.getMoves(this).none { m -> match.variant.isLegal(m, this) } }) {
            if (isInCheck(this, !match.currentColor))
                match.stop(match.currentColor.wonBy(EndReason.CHECKMATE))
            else
                match.stop(drawBy(EndReason.STALEMATE))
        }

        checkForRepetition()
        checkForFiftyMoveRule()

        val whitePieces = piecesOf(Color.WHITE)
        val blackPieces = piecesOf(Color.BLACK)
        if (whitePieces.size == 1 && blackPieces.size == 1)
            match.stop(drawBy(EndReason.INSUFFICIENT_MATERIAL))

        val minorPieces = listOf(PieceType.KNIGHT, PieceType.BISHOP)
        if (whitePieces.size == 2 && whitePieces.any { it.type in minorPieces } && blackPieces.size == 1)
            match.stop(drawBy(EndReason.INSUFFICIENT_MATERIAL))
        if (blackPieces.size == 2 && blackPieces.any { it.type in minorPieces } && whitePieces.size == 1)
            match.stop(drawBy(EndReason.INSUFFICIENT_MATERIAL))
    }

    open fun timeout(match: ChessMatch, color: Color) {
        if (match.board.piecesOf(!color).size == 1)
            match.stop(drawBy(EndReason.DRAW_TIMEOUT))
        else
            match.stop(color.lostBy(EndReason.TIMEOUT))
    }

    open fun getPieceMoves(piece: BoardPiece, board: ChessboardView, variantOptions: Long): List<Move> = when(piece.type) {
        PieceType.KING -> kingMovement(piece, board, Normal.variantOptionsToCastlingStyle(variantOptions))
        PieceType.QUEEN -> rays(piece, rotationsOf(1, 1) + rotationsOf(1, 0))
        PieceType.ROOK -> rays(piece, rotationsOf(1, 0))
        PieceType.BISHOP -> rays(piece, rotationsOf(1, 1))
        PieceType.KNIGHT -> jumps(piece, rotationsOf(2, 1))
        PieceType.PAWN -> pawnMovement(piece).promotions(Normal.PROMOTIONS)
        else -> throw IllegalArgumentException(piece.type.toString())
    }

    open fun startingPieceHasMoved(fen: FEN, pos: Pos, piece: Piece): Boolean = when(piece.type) {
        PieceType.PAWN -> when (piece.color) {
            Color.WHITE -> pos.rank != 1
            Color.BLACK -> pos.rank != 6
        }
        PieceType.ROOK -> pos.file !in fen.castlingRights[piece.color]
        else -> false
    }

    open fun genFEN(variantOptions: Long): FEN =
        if (!Normal.variantOptionsToCastlingStyle(variantOptions).chess960) FEN() else FEN.generateChess960()

    open fun addPGNTags(match: ChessMatch, tags: PGN.GenerateEvent) {
        val castlingStyle = Normal.variantOptionsToCastlingStyle(match.variantOptions)
        if (!match.board.initialFEN.isInitial() || castlingStyle.chess960) {
            tags["SetUp"] = "1"
            tags["FEN"] = match.board.initialFEN.toString()
        }
        val variant = buildList {
            if (match.variant != Normal)
                add(match.variant.name.snakeToPascal())
            if (castlingStyle.chess960)
                add("Chess960")
            if (castlingStyle.simpleCastling)
                add("SimpleCastling")
        }.joinToString(" ")

        if (variant.isNotBlank())
            tags["Variant"] = variant
    }

    open fun validateFEN(fen: FEN, variantOptions: Long) {
        val castlingStyle = Normal.variantOptionsToCastlingStyle(variantOptions)
        if (!castlingStyle.chess960)
            check(!fen.isChess960ForCastleRights())
        val pieces = fen.toPieces(this)
        check(pieces.count { it.value.type == PieceType.KING && it.value.color == Color.WHITE } == 1)
        check(pieces.count { it.value.type == PieceType.KING && it.value.color == Color.BLACK } == 1)
        val fakeBoard = Chessboard.createFakeConnector(this, variantOptions, fen)
        fakeBoard.updateMoves()
        check(!isInCheck(fakeBoard, !fen.currentColor))
    }

    open val pieceTypes: Collection<PieceType>
        get() = PieceType.run { listOf(KING, QUEEN, ROOK, BISHOP, KNIGHT, PAWN) }

    open val requiredComponents: Set<ComponentIdentifier<*>> get() = emptySet()

    open val pgnMoveFormatter: MoveFormatter = simpleMoveFormatter { move ->
        val main = move.pieceTracker.getOriginalOrNull("main")

        if (main?.piece?.type != PieceType.PAWN && move.castlesTrait == null) {
            +main?.piece?.char?.uppercase()
            +move.targetTrait?.uniquenessCoordinate
        }
        if (move.captureTrait?.captureSuccess == true) {
            if (main?.piece?.type == PieceType.PAWN)
                +(main as? BoardPiece)?.pos?.fileStr
            +"x"
        }
        +move.targetTrait?.target
        +move.castlesTrait?.side?.castles
        +move.promotionTrait?.promotion?.let { "="+it.char.uppercase() }
        +move.checkTrait?.checkType?.char
    }

    protected fun allMoves(color: Color, board: ChessboardView) = board.piecesOf(color).flatMap { it.getMoves(board) }

    protected fun Move.isPinnedBy(pin: Move, board: ChessboardView) =
        captureTrait?.capture != pin.origin &&
                pin.neededEmpty.filter { board[it] != null }.all { it in stopBlocking } &&
                startBlocking.none { it in pin.neededEmpty }

    object Normal : ChessVariant() {

        @JvmField
        val PROMOTIONS = with(PieceType) { listOf(QUEEN, ROOK, BISHOP, KNIGHT) }

        fun pinningMoves(by: Color, pos: Pos, board: ChessboardView) =
            allMoves(by, board).filter { it.captureTrait?.capture == pos }.filter { m ->
                m.requireFlagTrait?.flags.orEmpty().none { (p, fs) -> !fs.all { f -> board.hasActiveFlag(p, f) } } &&
                        m.neededEmpty.any { board[it]?.piece != null }
            }

        fun checkingMoves(by: Color, pos: Pos, board: ChessboardView) =
            allMoves(by, board).filter { it.captureTrait?.capture == pos }.filter { m ->
                m.requireFlagTrait?.flags.orEmpty().none { (p, fs) -> !fs.all { f -> board.hasActiveFlag(p, f) } } &&
                        m.neededEmpty.mapNotNull { board[it]?.piece }
                            .all { it.color == !m.main.color && it.type == PieceType.KING }
            }

        fun isValid(move: Move, board: ChessboardView): Boolean = with(move) {

            if (requireFlagTrait?.flags.orEmpty().any { (p, fs) -> !fs.all { f -> board.hasActiveFlag(p, f) } })
                return false

            if (neededEmpty.any { board[it] != null })
                return false

            captureTrait?.let {
                if (it.hasToCapture && board[it.capture] == null)
                    return false
                if (board[it.capture]?.color == main.color)
                    return false
            }

            return true
        }

        fun variantOptionsToCastlingStyle(variantOptions: Long): CastlingStyle = when(variantOptions) {
            0L -> CastlingStyle.NORMAL
            1L -> CastlingStyle.CHESS960
            2L -> CastlingStyle.SIMPLE
            else -> throw IllegalArgumentException(variantOptions.toString())
        }

    }

}