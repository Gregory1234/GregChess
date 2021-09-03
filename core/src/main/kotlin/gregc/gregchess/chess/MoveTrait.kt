package gregc.gregchess.chess

import gregc.gregchess.ClassRegisteredSerializer
import gregc.gregchess.RegistryType
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

class TraitsCouldNotExecuteException(traits: Collection<MoveTrait>):
    Exception(traits.toList().map { RegistryType.MOVE_TRAIT_CLASS.getModule(it::class).namespace + ":" + RegistryType.MOVE_TRAIT_CLASS[it::class] }.toString())

@Serializable(with = MoveTraitSerializer::class)
interface MoveTrait {
    val shouldComeBefore: Collection<KClass<out MoveTrait>> get() = emptyList()
    val shouldComeAfter: Collection<KClass<out MoveTrait>> get() = emptyList()
    val nameTokens: MoveName
    fun setup(game: ChessGame, move: Move) {}
    fun execute(game: ChessGame, move: Move, pass: UByte, remaining: List<MoveTrait>): Boolean = true
    fun undo(game: ChessGame, move: Move, pass: UByte, remaining: List<MoveTrait>): Boolean = true
}

object MoveTraitSerializer: ClassRegisteredSerializer<MoveTrait>("MoveTrait", RegistryType.MOVE_TRAIT_CLASS)

@Serializable
class DefaultHalfmoveClockTrait(var halfmoveClock: UInt? = null): MoveTrait {
    override val nameTokens = MoveName()

    override val shouldComeBefore = listOf(CaptureTrait::class)

    override fun execute(game: ChessGame, move: Move, pass: UByte, remaining: List<MoveTrait>): Boolean {
        halfmoveClock = game.board.halfmoveClock
        if (move.piece.type == PieceType.PAWN || move.getTrait<CaptureTrait>()?.captured != null) {
            game.board.halfmoveClock = 0u
        } else {
            game.board.halfmoveClock++
        }
        return true
    }

    override fun undo(game: ChessGame, move: Move, pass: UByte, remaining: List<MoveTrait>): Boolean {
        game.board.halfmoveClock = halfmoveClock!!
        return true
    }
}

@Serializable
class CastlesTrait(val rook: PieceInfo, val side: BoardSide, val target: Pos, val rookTarget: Pos): MoveTrait {
    override val nameTokens = MoveName(listOf(MoveNameTokenType.CASTLE.of(side)))
    override fun execute(game: ChessGame, move: Move, pass: UByte, remaining: List<MoveTrait>): Boolean {
        val boardPiece = game.board[move.piece.pos]?.piece
        val boardRook = game.board[rook.pos]?.piece
        val targetSquare = game.board[target]
        val rookTargetSquare = game.board[rookTarget]
        if (boardPiece == null || boardRook == null ||
            targetSquare == null || (target != rook.pos && targetSquare.piece != null) ||
            rookTargetSquare == null || (rookTarget != move.piece.pos && rookTargetSquare.piece != null)
        )
            return false
        BoardPiece.autoMove(mapOf(
            boardPiece to targetSquare,
            boardRook to rookTargetSquare
        ))
        return true
    }

    override fun undo(game: ChessGame, move: Move, pass: UByte, remaining: List<MoveTrait>): Boolean {
        val boardPiece = game.board[target]?.piece
        val boardRook = game.board[rookTarget]?.piece
        val targetSquare = game.board[move.piece.pos]
        val rookTargetSquare = game.board[rook.pos]
        if (boardPiece == null || boardRook == null ||
            targetSquare == null || (target != rook.pos && targetSquare.piece != null) ||
            rookTargetSquare == null || (rookTarget != move.piece.pos && rookTargetSquare.piece != null)
        )
            return false
        BoardPiece.autoMove(mapOf(
            boardPiece to targetSquare,
            boardRook to rookTargetSquare
        ))
        boardPiece.force(false)
        boardRook.force(false)
        return true
    }
}

@Serializable
class PromotionTrait(val promotions: List<Piece>? = null, var promotion: Piece? = null): MoveTrait {
    override val nameTokens = MoveName(listOfNotNull(promotion?.type?.let { MoveNameTokenType.PROMOTION.of(it) }))

    override val shouldComeAfter = listOf(TargetTrait::class)

    override fun execute(game: ChessGame, move: Move, pass: UByte, remaining: List<MoveTrait>): Boolean {
        if ((promotions == null) != (promotion == null))
            return false
        if (promotions?.contains(promotion) == false)
            return false
        promotion?.let {
            game.board[move.piece.pos]?.piece?.promote(it)
        }
        return true
    }

    override fun undo(game: ChessGame, move: Move, pass: UByte, remaining: List<MoveTrait>): Boolean {
        if (promotion != null) {
            if (game.board[move.piece.pos]?.piece == null)
                return false
            game.board[move.piece.pos]?.piece?.promote(move.piece.piece)
        }
        return true
    }
}

@Serializable
class NameTrait(override val nameTokens: MoveName): MoveTrait

private fun checkForChecks(side: Side, game: ChessGame): MoveNameToken<Unit>? {
    game.board.updateMoves()
    val pieces = game.board.piecesOf(!side)
    val inCheck = game.variant.isInCheck(game, !side)
    val noMoves = pieces.all { game.board.getMoves(it.pos).none { m -> game.variant.isLegal(m, game) } }
    return when {
        inCheck && noMoves -> MoveNameTokenType.CHECKMATE.mk
        inCheck -> MoveNameTokenType.CHECK.mk
        else -> null
    }
}


@Serializable
class CheckTrait(override val nameTokens: MoveName = MoveName()): MoveTrait {
    override fun execute(game: ChessGame, move: Move, pass: UByte, remaining: List<MoveTrait>): Boolean {
        if (remaining.all { it is CheckTrait }) {
            if (nameTokens.isEmpty())
                checkForChecks(move.piece.side, game)?.let { nameTokens += it }
            return true
        }
        return false
    }
}

@Serializable
class CaptureTrait(val capture: Pos, val hasToCapture: Boolean = false, var captured: PieceInfo? = null, var capturedPiece: CapturedPiece? = null): MoveTrait  {
    override val nameTokens get() = MoveName(listOfNotNull(MoveNameTokenType.CAPTURE.mk.takeIf { captured != null }))

    override fun execute(game: ChessGame, move: Move, pass: UByte, remaining: List<MoveTrait>): Boolean {
        game.board[capture]?.piece?.let {
            captured = it.info
            capturedPiece = it.capture(move.piece.side)
        }
        return true
    }

    override fun undo(game: ChessGame, move: Move, pass: UByte, remaining: List<MoveTrait>): Boolean {
        captured?.let {
            if (game.board[it.pos]?.piece != null)
                return false
            game.board += it
            game.board[it.pos]?.piece?.resurrect(capturedPiece!!)
        }
        return true
    }
}

@Serializable
class PawnOriginTrait(override val nameTokens: MoveName = MoveName()): MoveTrait {
    override fun setup(game: ChessGame, move: Move) {
        move.getTrait<CaptureTrait>()?.let {
            if (game.board[it.capture]?.piece != null && nameTokens.isEmpty()) {
                nameTokens += MoveNameTokenType.UNIQUENESS_COORDINATE.of(UniquenessCoordinate(file = move.piece.pos.file))
            }
        }
    }
}

private fun getUniquenessCoordinate(piece: PieceInfo, target: Pos, game: ChessGame): UniquenessCoordinate {
    val pieces = game.board.pieces.filter { it.side == piece.side && it.type == piece.type }
    val consideredPieces = pieces.filter { p ->
        p.square.bakedMoves.orEmpty().any { it.getTrait<TargetTrait>()?.target == target && game.variant.isLegal(it, game) }
    }
    return when {
        consideredPieces.size == 1 -> UniquenessCoordinate()
        consideredPieces.count { it.pos.file == piece.pos.file } == 1 -> UniquenessCoordinate(file = piece.pos.file)
        consideredPieces.count { it.pos.rank == piece.pos.rank } == 1 -> UniquenessCoordinate(rank = piece.pos.rank)
        else -> UniquenessCoordinate(piece.pos)
    }
}

@Serializable
class PieceOriginTrait(override val nameTokens: MoveName = MoveName()): MoveTrait {
    override fun setup(game: ChessGame, move: Move) {
        if (nameTokens.isEmpty()) {
            nameTokens += MoveNameTokenType.PIECE_TYPE.of(move.piece.type)
            move.getTrait<TargetTrait>()?.let {
                nameTokens += MoveNameTokenType.UNIQUENESS_COORDINATE.of(getUniquenessCoordinate(move.piece, it.target, game))
            }
        }
    }
}

@Serializable
class TargetTrait(val target: Pos, var hasMoved: Boolean = false): MoveTrait {
    override val nameTokens = MoveName(listOf(MoveNameTokenType.TARGET.of(target)))

    override val shouldComeBefore = listOf(CaptureTrait::class)

    override fun execute(game: ChessGame, move: Move, pass: UByte, remaining: List<MoveTrait>): Boolean {
        game.board[target].let { t ->
            if (t == null || t.piece != null)
                return false
            game.board[move.piece.pos]?.piece.let { p ->
                if (p?.piece != move.piece.piece)
                    return false
                hasMoved = p.hasMoved
                p.move(t)
            }
        }
        return true
    }

    override fun undo(game: ChessGame, move: Move, pass: UByte, remaining: List<MoveTrait>): Boolean {
        game.board[move.piece.pos].let { t ->
            if (t == null || t.piece != null)
                return false
            game.board[target]?.piece.let { p ->
                if (p?.piece != move.piece.piece)
                    return false
                p.move(t)
                p.force(hasMoved)
            }
        }
        return true
    }
}