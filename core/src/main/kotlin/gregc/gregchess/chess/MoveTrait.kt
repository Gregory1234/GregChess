package gregc.gregchess.chess

import gregc.gregchess.ClassRegisteredSerializer
import gregc.gregchess.RegistryType
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

class TraitsCouldNotExecuteException(traits: Collection<MoveTrait>) :
    Exception(traits.toList().map { RegistryType.MOVE_TRAIT_CLASS[it::class] }.toString())

@Serializable(with = MoveTraitSerializer::class)
interface MoveTrait {
    val shouldComeBefore: Collection<KClass<out MoveTrait>> get() = emptyList()
    val shouldComeAfter: Collection<KClass<out MoveTrait>> get() = emptyList()
    val nameTokens: List<AnyMoveNameToken>
    fun setup(game: ChessGame, move: Move) {}
    fun execute(game: ChessGame, move: Move, pass: UByte, remaining: List<MoveTrait>): Boolean = true
    fun undo(game: ChessGame, move: Move, pass: UByte, remaining: List<MoveTrait>): Boolean = true
}

inline fun tryPiece(f: () -> Unit): Boolean =
    try {
        f()
        true
    } catch (e: PieceDoesNotExistException) {
        false
    } catch (e: PieceAlreadyOccupiesSquareException) {
        false
    }

object MoveTraitSerializer : ClassRegisteredSerializer<MoveTrait>("MoveTrait", RegistryType.MOVE_TRAIT_CLASS)

@Serializable
class DefaultHalfmoveClockTrait : MoveTrait {
    override val nameTokens = emptyList<AnyMoveNameToken>()

    override val shouldComeBefore = listOf(CaptureTrait::class)

    private var halfmoveClock: UInt = 0u

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
        game.board.halfmoveClock = halfmoveClock
        return true
    }
}

@Serializable
class CastlesTrait(val rook: BoardPiece, val side: BoardSide, val target: Pos, val rookTarget: Pos) : MoveTrait {

    override val nameTokens = nameOf(MoveNameTokenType.CASTLE.of(side))

    var resulting: BoardPiece? = null
        private set
    var rookResulting: BoardPiece? = null
        private set

    override fun execute(game: ChessGame, move: Move, pass: UByte, remaining: List<MoveTrait>): Boolean {
        return tryPiece {
            BoardPiece.autoMove(mapOf(move.piece to target, rook to rookTarget), game.board).map { (o, t) ->
                when (o) {
                    rook -> rookResulting = t
                    move.piece -> resulting = t
                }
            }
        }
    }

    override fun undo(game: ChessGame, move: Move, pass: UByte, remaining: List<MoveTrait>): Boolean {
        return tryPiece {
            BoardPiece.autoMove(
                mapOf(
                    (resulting ?: return false) to move.piece.pos,
                    (rookResulting ?: return false) to rook.pos
                ), game.board
            ).map { (_, t) ->
                t.copyInPlace(game.board, hasMoved = false)
            }
        }
    }
}

@Serializable
class PromotionTrait(val promotions: List<Piece>) : MoveTrait {

    var promotion: Piece? = null

    var promoted: BoardPiece? = null
        private set

    override val nameTokens =
        listOfNotNull(promotion?.type?.let { AnyMoveNameToken(MoveNameTokenType.PROMOTION.of(it)) })

    override val shouldComeBefore = listOf(TargetTrait::class)

    override fun execute(game: ChessGame, move: Move, pass: UByte, remaining: List<MoveTrait>): Boolean {
        val promotion = promotion ?: return false
        if (promotion !in promotions)
            return false
        return tryPiece {
            promoted = (move.getTrait<TargetTrait>()?.resulting ?: move.piece).promote(promotion, game.board)
        }
    }

    override fun undo(game: ChessGame, move: Move, pass: UByte, remaining: List<MoveTrait>): Boolean {
        return tryPiece {
            val promoted = promoted ?: return false
            promoted.promote(move.piece.piece, game.board)
                .copyInPlace(game.board, hasMoved = (move.getTrait<TargetTrait>()?.resulting ?: move.piece).hasMoved)
        }
    }
}

@Serializable
class NameTrait(override val nameTokens: MoveName) : MoveTrait

private fun checkForChecks(color: Color, game: ChessGame): MoveNameToken<Unit>? {
    game.board.updateMoves()
    val pieces = game.board.piecesOf(!color)
    val inCheck = game.variant.isInCheck(game, !color)
    val noMoves = pieces.all { it.getMoves(game.board).none { m -> game.variant.isLegal(m, game) } }
    return when {
        inCheck && noMoves -> MoveNameTokenType.CHECKMATE.mk
        inCheck -> MoveNameTokenType.CHECK.mk
        else -> null
    }
}


@Serializable
class CheckTrait : MoveTrait {

    override val nameTokens: MutableTokenList = emptyTokenList()

    override fun execute(game: ChessGame, move: Move, pass: UByte, remaining: List<MoveTrait>): Boolean {
        if (remaining.all { it is CheckTrait }) {
            if (nameTokens.isEmpty())
                checkForChecks(move.piece.color, game)?.let { nameTokens += it }
            return true
        }
        return false
    }
}

@Serializable
class CaptureTrait(val capture: Pos, val hasToCapture: Boolean = false, var captured: CapturedBoardPiece? = null) :
    MoveTrait {

    override val nameTokens get() =
        listOfNotNull(AnyMoveNameToken(MoveNameTokenType.CAPTURE.mk).takeIf { captured != null })

    override fun execute(game: ChessGame, move: Move, pass: UByte, remaining: List<MoveTrait>): Boolean {
        game.board[capture]?.piece?.let {
            captured = it.capture(move.piece.color, game.board)
        }
        return true
    }

    override fun undo(game: ChessGame, move: Move, pass: UByte, remaining: List<MoveTrait>): Boolean {
        captured?.let {
            if (game.board[it.pos]?.piece != null)
                return false
            it.resurrect(game.board)
        }
        return true
    }
}

@Serializable
class PawnOriginTrait : MoveTrait {
    override val nameTokens = emptyTokenList()

    override fun setup(game: ChessGame, move: Move) {
        move.getTrait<CaptureTrait>()?.let {
            if (game.board[it.capture]?.piece != null && nameTokens.isEmpty()) {
                nameTokens += MoveNameTokenType.UNIQUENESS_COORDINATE.of(UniquenessCoordinate(file = move.piece.pos.file))
            }
        }
    }
}

private fun getUniquenessCoordinate(piece: BoardPiece, target: Pos, game: ChessGame): UniquenessCoordinate {
    val pieces = game.board.pieces.filter { it.color == piece.color && it.type == piece.type }
    val consideredPieces = pieces.filter { p ->
        p.getMoves(game.board).any { it.getTrait<TargetTrait>()?.target == target && game.variant.isLegal(it, game) }
    }
    return when {
        consideredPieces.size == 1 -> UniquenessCoordinate()
        consideredPieces.count { it.pos.file == piece.pos.file } == 1 -> UniquenessCoordinate(file = piece.pos.file)
        consideredPieces.count { it.pos.rank == piece.pos.rank } == 1 -> UniquenessCoordinate(rank = piece.pos.rank)
        else -> UniquenessCoordinate(piece.pos)
    }
}

@Serializable
class PieceOriginTrait : MoveTrait {
    override val nameTokens = emptyTokenList()

    override fun setup(game: ChessGame, move: Move) {
        if (nameTokens.isEmpty()) {
            nameTokens += MoveNameTokenType.PIECE_TYPE.of(move.piece.type)
            move.getTrait<TargetTrait>()?.let {
                nameTokens +=
                    MoveNameTokenType.UNIQUENESS_COORDINATE.of(getUniquenessCoordinate(move.piece, it.target, game))
            }
        }
    }
}

@Serializable
class TargetTrait(val target: Pos) : MoveTrait {
    override val nameTokens = nameOf(MoveNameTokenType.TARGET.of(target))

    override val shouldComeBefore = listOf(CaptureTrait::class)

    var resulting: BoardPiece? = null
        private set

    override fun execute(game: ChessGame, move: Move, pass: UByte, remaining: List<MoveTrait>): Boolean {
        return tryPiece {
            resulting = move.piece.move(target, game.board)
        }
    }

    override fun undo(game: ChessGame, move: Move, pass: UByte, remaining: List<MoveTrait>): Boolean {
        return tryPiece {
            (resulting ?: return false).move(move.piece.pos, game.board)
                .copyInPlace(game.board, hasMoved = move.piece.hasMoved)
        }
    }
}