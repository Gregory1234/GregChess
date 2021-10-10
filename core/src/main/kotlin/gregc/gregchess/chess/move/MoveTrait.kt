package gregc.gregchess.chess.move

import gregc.gregchess.chess.*
import gregc.gregchess.chess.piece.*
import gregc.gregchess.registry.ClassRegisteredSerializer
import gregc.gregchess.registry.RegistryType
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

class TraitsCouldNotExecuteException(traits: Collection<MoveTrait>) :
    Exception(traits.toList().map { RegistryType.MOVE_TRAIT_CLASS[it::class] }.toString())

class TraitPreconditionException(trait: MoveTrait, message: String, cause: Throwable? = null) :
    IllegalStateException("${trait::class.traitKey}: $message", cause)

@Serializable(with = MoveTraitSerializer::class)
interface MoveTrait {
    val shouldComeFirst: Boolean get() = false
    val shouldComeLast: Boolean get() = false
    val shouldComeBefore: Collection<KClass<out MoveTrait>> get() = emptyList()
    val shouldComeAfter: Collection<KClass<out MoveTrait>> get() = emptyList()
    val nameTokens: List<AnyMoveNameToken>
    fun setup(game: ChessGame, move: Move) {}
    fun execute(game: ChessGame, move: Move) {}
    fun undo(game: ChessGame, move: Move) {}
}

inline fun MoveTrait.tryPiece(f: () -> Unit) =
    try {
        f()
    } catch (e: PieceDoesNotExistException) {
        throw pieceNotExist(e)
    } catch (e: PieceAlreadyOccupiesSquareException) {
        pieceOccupiesSquare(e)
    }

object MoveTraitSerializer : ClassRegisteredSerializer<MoveTrait>("MoveTrait", RegistryType.MOVE_TRAIT_CLASS)

fun MoveTrait.pieceNotExist(e: PieceDoesNotExistException): Nothing =
    throw TraitPreconditionException(this, "Piece ${e.piece.piece} doesn't exist at ${e.piece.pos}!", e)
fun MoveTrait.pieceOccupiesSquare(e: PieceAlreadyOccupiesSquareException): Nothing =
    throw TraitPreconditionException(this, "Piece ${e.piece.piece} occupies square ${e.piece.pos}!", e)
fun MoveTrait.traitNotExecuted(): Nothing =
    throw TraitPreconditionException(this, "Trait hasn't executed")

val KClass<out MoveTrait>.traitKey get() = RegistryType.MOVE_TRAIT_CLASS[this]
val KClass<out MoveTrait>.traitModule get() = traitKey.module
val KClass<out MoveTrait>.traitName get() = traitKey.key

@Serializable
class DefaultHalfmoveClockTrait : MoveTrait {
    override val nameTokens = emptyList<AnyMoveNameToken>()

    override val shouldComeBefore = listOf(CaptureTrait::class)

    private var halfmoveClock: UInt = 0u

    override fun execute(game: ChessGame, move: Move) {
        halfmoveClock = game.board.halfmoveClock
        if (move.piece.type == PieceType.PAWN || move.getTrait<CaptureTrait>()?.captured != null) {
            game.board.halfmoveClock = 0u
        } else {
            game.board.halfmoveClock++
        }
    }

    override fun undo(game: ChessGame, move: Move) {
        game.board.halfmoveClock = halfmoveClock
    }
}

@Serializable
class CastlesTrait(val rook: BoardPiece, val side: BoardSide, val target: Pos, val rookTarget: Pos) : MoveTrait {

    override val nameTokens = nameOf(MoveNameTokenType.CASTLE.of(side))

    var resulting: BoardPiece? = null
        private set
    var rookResulting: BoardPiece? = null
        private set

    override fun execute(game: ChessGame, move: Move) = tryPiece {
        BoardPiece.autoMove(mapOf(move.piece to target, rook to rookTarget), game.board).map { (o, t) ->
            when (o) {
                rook -> rookResulting = t
                move.piece -> resulting = t
            }
        }
    }

    override fun undo(game: ChessGame, move: Move) = tryPiece {
        BoardPiece.autoMove(
            mapOf(
                (resulting ?: traitNotExecuted()) to move.piece.pos,
                (rookResulting ?: traitNotExecuted()) to rook.pos
            ), game.board
        ).map { (_, t) ->
            t.copyInPlace(game.board, hasMoved = false)
        }
    }
}

@Serializable
class PromotionTrait(val promotions: List<Piece>) : MoveTrait {

    var promotion: Piece? = null

    var promoted: BoardPiece? = null
        private set

    override val nameTokens
        get() = listOfNotNull(promotion?.type?.let { AnyMoveNameToken(MoveNameTokenType.PROMOTION.of(it)) })

    override val shouldComeBefore = listOf(TargetTrait::class)

    override fun execute(game: ChessGame, move: Move) {
        val promotion = promotion ?: throw TraitPreconditionException(this, "Promotion not chosen", NullPointerException())
        if (promotion !in promotions) throw TraitPreconditionException(this, "Promotion not valid: $promotion")
        tryPiece {
            promoted = (move.getTrait<TargetTrait>()?.resulting ?: move.piece).promote(promotion, game.board)
        }
    }

    override fun undo(game: ChessGame, move: Move) = tryPiece {
        val promoted = promoted ?: traitNotExecuted()
        promoted.promote(move.piece.piece, game.board)
            .copyInPlace(game.board, hasMoved = (move.getTrait<TargetTrait>()?.resulting ?: move.piece).hasMoved)
    }
}

@Serializable
class NameTrait(override val nameTokens: MoveName) : MoveTrait

@Serializable
class FlagTrait(val flags: Collection<PosFlag>) : MoveTrait {
    override val nameTokens = emptyList<AnyMoveNameToken>()

    override fun execute(game: ChessGame, move: Move) {
        for ((p, f) in flags)
            game.board[p]!!.flags += f
    }
}

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

    override val shouldComeLast: Boolean = true

    override val nameTokens: MutableTokenList = emptyTokenList()

    override fun execute(game: ChessGame, move: Move) {
        checkForChecks(move.piece.color, game)?.let { nameTokens += it }
    }
}

@Serializable
class CaptureTrait(val capture: Pos, val hasToCapture: Boolean = false, var captured: CapturedBoardPiece? = null) :
    MoveTrait {

    override val nameTokens get() =
        listOfNotNull(AnyMoveNameToken(MoveNameTokenType.CAPTURE.mk).takeIf { captured != null })

    override fun execute(game: ChessGame, move: Move) {
        game.board[capture]?.piece?.let {
            captured = it.capture(move.piece.color, game.board)
        }
    }

    override fun undo(game: ChessGame, move: Move) = tryPiece {
        captured?.resurrect(game.board)
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

    override fun execute(game: ChessGame, move: Move) = tryPiece {
        resulting = move.piece.move(target, game.board)
    }

    override fun undo(game: ChessGame, move: Move) = tryPiece {
        (resulting ?: traitNotExecuted()).move(move.piece.pos, game.board)
            .copyInPlace(game.board, hasMoved = move.piece.hasMoved)
    }
}