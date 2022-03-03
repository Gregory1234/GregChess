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
    fun execute(game: ChessGame, move: Move) {} // TODO: create a move execution context instead of providing a full game
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
        val REQUIRE_FLAG = MoveTraitType(RequireFlagTrait.serializer())
        @JvmField
        val FLAG = MoveTraitType(FlagTrait.serializer())
        @JvmField
        val CHECK = MoveTraitType(CheckTrait.serializer())
        @JvmField
        val CAPTURE = MoveTraitType(CaptureTrait.serializer())
        @JvmField
        val TARGET = MoveTraitType(TargetTrait.serializer())
        @JvmField
        val SPAWN = MoveTraitType(SpawnTrait.serializer())
        @JvmField
        val CLEAR = MoveTraitType(ClearTrait.serializer())

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

    override val shouldComeBefore get() = setOf(MoveTraitType.CAPTURE)

    private var halfmoveClock: UInt = 0u

    override fun execute(game: ChessGame, move: Move) {
        halfmoveClock = game.board.halfmoveClock
        if (move.main.type == PieceType.PAWN || move.captureTrait?.captureSuccess != true) {
            game.board.halfmoveClock = 0u
        } else {
            game.board.halfmoveClock++
        }
    }

    override fun undo(game: ChessGame, move: Move) {
        game.board.halfmoveClock = halfmoveClock
    }
}

val Move.halfmoveClockTrait get() = get(MoveTraitType.HALFMOVE_CLOCK)

@Serializable
class CastlesTrait(val side: BoardSide, val target: Pos, val rookTarget: Pos) : MoveTrait {
    override val type get() = MoveTraitType.CASTLES

    private val Move.rook get() = pieceTracker["rook"] as BoardPiece

    override fun execute(game: ChessGame, move: Move) = tryPiece {
        move.pieceTracker.traceMove(game.board, move.main.boardPiece().move(target), move.rook.move(rookTarget))
    }

    override fun undo(game: ChessGame, move: Move) = tryPiece {
        move.pieceTracker.traceMoveBack(game.board, move.main, move.rook)
    }
}

val Move.castlesTrait get() = get(MoveTraitType.CASTLES)

@Serializable
class PromotionTrait(val promotions: List<Piece>) : MoveTrait {
    override val type get() = MoveTraitType.PROMOTION

    var promotion: Piece? = null

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

val Move.promotionTrait get() = get(MoveTraitType.PROMOTION)

@Serializable
class RequireFlagTrait(val flags: Map<Pos, Set<ChessFlag>>) : MoveTrait {
    override val type get() = MoveTraitType.REQUIRE_FLAG
}

val Move.requireFlagTrait get() = get(MoveTraitType.REQUIRE_FLAG)

@Serializable
class FlagTrait(val flags: Map<Pos, Map<ChessFlag, UInt>>) : MoveTrait {
    override val type get() = MoveTraitType.FLAG

    override fun execute(game: ChessGame, move: Move) {
        for ((p, f) in flags)
            for ((t, a) in f)
                game.board.addFlag(p, t, a)
    }
}

val Move.flagTrait get() = get(MoveTraitType.FLAG)

enum class CheckType(val char: Char) {
    CHECK('+'), CHECKMATE('#')
}

@Serializable
class CheckTrait : MoveTrait {
    override val type get() = MoveTraitType.CHECK

    override val shouldComeLast: Boolean = true

    var checkType: CheckType? = null
        private set

    private fun checkForChecks(color: Color, game: ChessGame): CheckType? {
        game.board.updateMoves()
        val pieces = game.board.piecesOf(!color)
        val inCheck = game.variant.isInCheck(game.board, !color)
        val noMoves = pieces.all { it.getMoves(game.board).none { m -> game.variant.isLegal(m, game.board) } }
        return when {
            inCheck && noMoves -> CheckType.CHECKMATE
            inCheck -> CheckType.CHECK
            else -> null
        }
    }

    override fun execute(game: ChessGame, move: Move) {
        checkType = checkForChecks(move.main.color, game)
    }
}

val Move.checkTrait get() = get(MoveTraitType.CHECK)

@Serializable
class CaptureTrait(val capture: Pos, val hasToCapture: Boolean = false, val by: Color? = null) : MoveTrait {
    override val type get() = MoveTraitType.CAPTURE

    private val Move.toCapture: BoardPiece get() = pieceTracker["capture"] as BoardPiece
    private val Move.captured: CapturedPiece get() = pieceTracker["capture"] as CapturedPiece

    var captureSuccess: Boolean = false
        private set

    override fun execute(game: ChessGame, move: Move) {
        game.board[capture]?.let {
            move.pieceTracker.giveName("capture", it)
            move.pieceTracker.traceMove(game.board, move.toCapture.capture(by ?: move.main.color))
            captureSuccess = true
        }
    }

    override fun undo(game: ChessGame, move: Move) = tryPiece {
        if (captureSuccess)
            move.pieceTracker.traceMoveBack(game.board, move.captured)
    }
}

val Move.captureTrait get() = get(MoveTraitType.CAPTURE)

@Serializable
class TargetTrait(val target: Pos) : MoveTrait {
    override val type get() = MoveTraitType.TARGET

    lateinit var uniquenessCoordinate: UniquenessCoordinate
        private set

    override val shouldComeBefore get() = setOf(MoveTraitType.CAPTURE)

    private fun getUniquenessCoordinate(piece: BoardPiece, target: Pos, game: ChessGame): UniquenessCoordinate {
        val pieces = game.board.pieces.filter { it.color == piece.color && it.type == piece.type }
        val consideredPieces = pieces.filter { p ->
            p.getLegalMoves(game.board).any { it.targetTrait?.target == target }
        }
        return when {
            consideredPieces.size == 1 -> UniquenessCoordinate()
            consideredPieces.count { it.pos.file == piece.pos.file } == 1 -> UniquenessCoordinate(file = piece.pos.file)
            consideredPieces.count { it.pos.rank == piece.pos.rank } == 1 -> UniquenessCoordinate(rank = piece.pos.rank)
            else -> UniquenessCoordinate(piece.pos)
        }
    }

    override fun execute(game: ChessGame, move: Move) = tryPiece {
        uniquenessCoordinate = getUniquenessCoordinate(move.main.boardPiece(), target, game)
        move.pieceTracker.traceMove(game.board, move.main.boardPiece().move(target))
    }

    override fun undo(game: ChessGame, move: Move) = tryPiece {
        move.pieceTracker.traceMoveBack(game.board, move.main)
    }
}

val Move.targetTrait get() = get(MoveTraitType.TARGET)

@Serializable
class SpawnTrait(val piece: BoardPiece) : MoveTrait {
    override val type get() = MoveTraitType.SPAWN

    override val shouldComeBefore get() = setOf(MoveTraitType.TARGET, MoveTraitType.CAPTURE)

    override fun execute(game: ChessGame, move: Move) = tryPiece {
        game.board.spawn(piece)
    }

    override fun undo(game: ChessGame, move: Move) = tryPiece {
        game.board.clear(piece)
    }

}

val Move.spawnTrait get() = get(MoveTraitType.SPAWN)

@Serializable
class ClearTrait(val piece: BoardPiece) : MoveTrait {
    override val type get() = MoveTraitType.CLEAR

    override val shouldComeAfter get() = setOf(MoveTraitType.TARGET)

    override fun execute(game: ChessGame, move: Move) = tryPiece {
        game.board.clear(piece)
    }

    override fun undo(game: ChessGame, move: Move) = tryPiece {
        game.board.spawn(piece)
    }

}

val Move.clearTrait get() = get(MoveTraitType.CLEAR)