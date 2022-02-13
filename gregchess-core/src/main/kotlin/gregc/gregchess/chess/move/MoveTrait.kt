package gregc.gregchess.chess.move

import gregc.gregchess.*
import gregc.gregchess.chess.*
import gregc.gregchess.chess.piece.*
import gregc.gregchess.registry.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

class TraitsCouldNotExecuteException(traits: Collection<MoveTrait>, cause: Throwable? = null) :
    Exception(traits.toList().map { Registry.MOVE_TRAIT_TYPE[it.type] }.toString(), cause)

class TraitPreconditionException(trait: MoveTrait, message: String, cause: Throwable? = null) :
    IllegalStateException("${trait.type}: $message", cause)

@Serializable(with = MoveTraitSerializer::class)
interface MoveTrait {
    val type: MoveTraitType<out @SelfType MoveTrait>
    val shouldComeFirst: Boolean get() = false
    val shouldComeLast: Boolean get() = false
    val shouldComeBefore: Set<MoveTraitType<*>> get() = emptySet()
    val shouldComeAfter: Set<MoveTraitType<*>> get() = emptySet()
    val nameTokens: MoveName
    fun setup(game: ChessGame, move: Move) {}
    fun execute(game: ChessGame, move: Move) {}
    fun undo(game: ChessGame, move: Move) {}
}

@Serializable(with = MoveTraitType.Serializer::class)
class MoveTraitType<T : MoveTrait>(val serializer: KSerializer<T>): NameRegistered {
    object Serializer : NameRegisteredSerializer<MoveTraitType<*>>("MoveTraitType", Registry.MOVE_TRAIT_TYPE)

    override val key get() = Registry.MOVE_TRAIT_TYPE[this]

    override fun toString(): String = Registry.MOVE_TRAIT_TYPE.simpleElementToString(this)

    @RegisterAll(MoveTraitType::class)
    companion object {

        internal val AUTO_REGISTER = AutoRegisterType(MoveTraitType::class) { m, n, _ -> register(m, n) }

        @JvmField
        val HALFMOVE_CLOCK = MoveTraitType(DefaultHalfmoveClockTrait.serializer())
        @JvmField
        val CASTLES = MoveTraitType(CastlesTrait.serializer())
        @JvmField
        val PROMOTION = MoveTraitType(PromotionTrait.serializer())
        @JvmField
        val NAME = MoveTraitType(NameTrait.serializer())
        @JvmField
        val REQUIRE_FLAG = MoveTraitType(RequireFlagTrait.serializer())
        @JvmField
        val FLAG = MoveTraitType(FlagTrait.serializer())
        @JvmField
        val CHECK = MoveTraitType(CheckTrait.serializer())
        @JvmField
        val CAPTURE = MoveTraitType(CaptureTrait.serializer())
        @JvmField
        @Register("pawn_move")
        val PAWN_ORIGIN = MoveTraitType(PawnOriginTrait.serializer())
        @JvmField
        @Register("piece_move")
        val PIECE_ORIGIN = MoveTraitType(PieceOriginTrait.serializer())
        @JvmField
        val TARGET = MoveTraitType(TargetTrait.serializer())

        fun registerCore(module: ChessModule) = AutoRegister(module, listOf(AUTO_REGISTER)).registerAll<MoveTraitType<*>>()
    }
}

inline fun MoveTrait.tryPiece(f: () -> Unit) =
    try {
        f()
    } catch (e: PieceDoesNotExistException) {
        pieceNotExist(e)
    } catch (e: PieceAlreadyOccupiesSquareException) {
        pieceOccupiesSquare(e)
    }

object MoveTraitSerializer : KeyRegisteredSerializer<MoveTraitType<*>, MoveTrait>("MoveTrait", MoveTraitType.Serializer) {

    @Suppress("UNCHECKED_CAST")
    override fun MoveTraitType<*>.valueSerializer(module: SerializersModule): KSerializer<MoveTrait> =
        serializer as KSerializer<MoveTrait>

    override val MoveTrait.key: MoveTraitType<*> get() = type

}

fun MoveTrait.pieceNotExist(e: PieceDoesNotExistException): Nothing =
    throw TraitPreconditionException(this, "Piece ${e.piece} doesn't exist!", e)
fun MoveTrait.pieceOccupiesSquare(e: PieceAlreadyOccupiesSquareException): Nothing =
    throw TraitPreconditionException(this, "Piece ${e.piece} occupies square already!", e)
fun MoveTrait.traitNotExecuted(): Nothing =
    throw TraitPreconditionException(this, "Trait hasn't executed")

@Serializable
class DefaultHalfmoveClockTrait : MoveTrait {
    override val type get() = MoveTraitType.HALFMOVE_CLOCK

    override val nameTokens get() = MoveName(emptyMap())

    override val shouldComeBefore get() = setOf(MoveTraitType.CAPTURE)

    private var halfmoveClock: UInt = 0u

    override fun execute(game: ChessGame, move: Move) {
        halfmoveClock = game.board.halfmoveClock
        if (move.main.type == PieceType.PAWN || move.getTrait<CaptureTrait>()?.captureSuccess != true) {
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
class CastlesTrait(val side: BoardSide, val target: Pos, val rookTarget: Pos) : MoveTrait {
    override val type get() = MoveTraitType.CASTLES

    override val nameTokens get() = MoveName(mapOf(MoveNameTokenType.CASTLE to side))

    private val Move.rook get() = pieceTracker["rook"] as BoardPiece

    override fun execute(game: ChessGame, move: Move) = tryPiece {
        move.pieceTracker.traceMove(game.board, move.main.boardPiece().move(target), move.rook.move(rookTarget))
    }

    override fun undo(game: ChessGame, move: Move) = tryPiece {
        move.pieceTracker.traceMoveBack(game.board, move.main, move.rook)
    }
}

@Serializable
class PromotionTrait(val promotions: List<Piece>) : MoveTrait {
    override val type get() = MoveTraitType.PROMOTION

    var promotion: Piece? = null

    override val nameTokens
        get() = MoveName(promotion?.type?.let { mapOf(MoveNameTokenType.PROMOTION to it) } ?: emptyMap())

    override val shouldComeBefore get() = setOf(MoveTraitType.TARGET)

    override fun execute(game: ChessGame, move: Move) {
        val promotion = promotion ?: throw TraitPreconditionException(this, "Promotion not chosen", NullPointerException())
        if (promotion !in promotions) throw TraitPreconditionException(this, "Promotion not valid: $promotion")
        tryPiece {
            move.pieceTracker.traceMove(game.board, move.main.boardPiece().promote(promotion))
        }
    }

    override fun undo(game: ChessGame, move: Move) = tryPiece {
        move.pieceTracker.traceMoveBack(game.board, move.main)
    }
}

@Serializable
class NameTrait(override val nameTokens: MoveName) : MoveTrait {
    override val type get() = MoveTraitType.NAME
}

@Serializable
class RequireFlagTrait(val flags: Map<Pos, Set<ChessFlag>>) : MoveTrait {
    override val type get() = MoveTraitType.REQUIRE_FLAG

    override val nameTokens get() = MoveName(emptyMap())
}

@Serializable
class FlagTrait(val flags: Map<Pos, Map<ChessFlag, UInt>>) : MoveTrait {
    override val type get() = MoveTraitType.FLAG

    override val nameTokens get() = MoveName(emptyMap())

    override fun execute(game: ChessGame, move: Move) {
        for ((p, f) in flags)
            for ((t, a) in f)
                game.board.addFlag(p, t, a)
    }
}

private fun checkForChecks(color: Color, game: ChessGame): MoveNameTokenType<Unit>? {
    game.board.updateMoves()
    val pieces = game.board.piecesOf(!color)
    val inCheck = game.variant.isInCheck(game, !color)
    val noMoves = pieces.all { it.getMoves(game.board).none { m -> game.variant.isLegal(m, game) } }
    return when {
        inCheck && noMoves -> MoveNameTokenType.CHECKMATE
        inCheck -> MoveNameTokenType.CHECK
        else -> null
    }
}


@Serializable
class CheckTrait : MoveTrait {
    override val type get() = MoveTraitType.CHECK

    override val shouldComeLast: Boolean = true

    private var checkToken: MoveNameTokenType<Unit>? = null

    override val nameTokens get() = MoveName(checkToken?.let { mapOf(it to Unit) } ?: emptyMap())

    override fun execute(game: ChessGame, move: Move) {
        checkToken = checkForChecks(move.main.color, game)
    }
}

@Serializable
class CaptureTrait(val capture: Pos, val hasToCapture: Boolean = false) : MoveTrait {
    override val type get() = MoveTraitType.CAPTURE

    private val Move.toCapture: BoardPiece get() = pieceTracker["capture"] as BoardPiece
    private val Move.captured: CapturedPiece get() = pieceTracker["capture"] as CapturedPiece

    var captureSuccess: Boolean = false
        private set

    override val nameTokens
        get() = MoveName(if (captureSuccess) mapOf(MoveNameTokenType.CAPTURE to Unit) else emptyMap())

    override fun execute(game: ChessGame, move: Move) {
        game.board[capture]?.let {
            move.pieceTracker.giveName("capture", it)
            move.pieceTracker.traceMove(game.board, move.toCapture.capture(move.main.color))
            captureSuccess = true
        }
    }

    override fun undo(game: ChessGame, move: Move) = tryPiece {
        if (captureSuccess)
            move.pieceTracker.traceMoveBack(game.board, move.captured)
    }
}

@Serializable
class PawnOriginTrait : MoveTrait {
    override val type get() = MoveTraitType.PAWN_ORIGIN

    private var uniquenessCoordinate: UniquenessCoordinate? = null

    override val nameTokens
        get() = MoveName(uniquenessCoordinate?.let {
            mapOf(MoveNameTokenType.UNIQUENESS_COORDINATE to it)
        } ?: emptyMap())

    override fun setup(game: ChessGame, move: Move) {
        move.getTrait<CaptureTrait>()?.let {
            if (game.board[it.capture] != null) {
                uniquenessCoordinate = UniquenessCoordinate(file = move.origin.file)
            }
        }
    }
}

private fun getUniquenessCoordinate(piece: BoardPiece, target: Pos, game: ChessGame): UniquenessCoordinate? {
    val pieces = game.board.pieces.filter { it.color == piece.color && it.type == piece.type }
    val consideredPieces = pieces.filter { p ->
        p.getMoves(game.board).any { it.getTrait<TargetTrait>()?.target == target && game.variant.isLegal(it, game) }
    }
    return when {
        consideredPieces.size == 1 -> null
        consideredPieces.count { it.pos.file == piece.pos.file } == 1 -> UniquenessCoordinate(file = piece.pos.file)
        consideredPieces.count { it.pos.rank == piece.pos.rank } == 1 -> UniquenessCoordinate(rank = piece.pos.rank)
        else -> UniquenessCoordinate(piece.pos)
    }
}

@Serializable
class PieceOriginTrait : MoveTrait {
    override val type get() = MoveTraitType.PIECE_ORIGIN

    private lateinit var pieceType: PieceType
    private var uniquenessCoordinate: UniquenessCoordinate? = null

    override val nameTokens
        get() = MoveName(
            mapOf(MoveNameTokenType.PIECE_TYPE to pieceType) +
                (uniquenessCoordinate?.let { mapOf(MoveNameTokenType.UNIQUENESS_COORDINATE to it) } ?: emptyMap())
        )

    override fun setup(game: ChessGame, move: Move) {
        pieceType = move.main.type
        move.getTrait<TargetTrait>()?.let {
            uniquenessCoordinate = getUniquenessCoordinate(move.main.boardPiece(), it.target, game)
        }
    }
}

@Serializable
class TargetTrait(val target: Pos) : MoveTrait {
    override val type get() = MoveTraitType.TARGET

    override val nameTokens get() = MoveName(mapOf(MoveNameTokenType.TARGET to target))

    override val shouldComeBefore get() = setOf(MoveTraitType.CAPTURE)

    override fun execute(game: ChessGame, move: Move) = tryPiece {
        move.pieceTracker.traceMove(game.board, move.main.boardPiece().move(target))
    }

    override fun undo(game: ChessGame, move: Move) = tryPiece {
        move.pieceTracker.traceMoveBack(game.board, move.main)
    }
}