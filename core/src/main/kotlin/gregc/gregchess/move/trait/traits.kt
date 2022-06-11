package gregc.gregchess.move.trait

import gregc.gregchess.*
import gregc.gregchess.move.Move
import gregc.gregchess.move.MoveEnvironment
import gregc.gregchess.move.connector.*
import gregc.gregchess.piece.*
import kotlinx.serialization.Serializable

@Serializable
class DefaultHalfmoveClockTrait : MoveTrait {
    override val type get() = MoveTraitType.HALFMOVE_CLOCK

    override val shouldComeBefore get() = setOf(MoveTraitType.CAPTURE)

    private var halfmoveClock: Int = 0

    override fun execute(env: MoveEnvironment, move: Move) {
        halfmoveClock = env.board.halfmoveClock
        if (move.main.type == PieceType.PAWN || move.captureTrait?.captureSuccess != true) {
            env.board.halfmoveClock = 0
        } else {
            env.board.halfmoveClock++
        }
    }

    override fun undo(env: MoveEnvironment, move: Move) {
        env.board.halfmoveClock = halfmoveClock
    }
}

val Move.halfmoveClockTrait get() = get(MoveTraitType.HALFMOVE_CLOCK)

@Serializable
class CastlesTrait(val side: BoardSide, val target: Pos, val rookTarget: Pos) : MoveTrait {
    override val type get() = MoveTraitType.CASTLES

    private val Move.rook get() = pieceTracker["rook"] as BoardPiece

    override fun execute(env: MoveEnvironment, move: Move) {
        move.pieceTracker.traceMove(env, move.main.boardPiece().move(target), move.rook.move(rookTarget))
    }

    override fun undo(env: MoveEnvironment, move: Move) {
        move.pieceTracker.traceMoveBack(env, move.main, move.rook)
    }
}

val Move.castlesTrait get() = get(MoveTraitType.CASTLES)

@Serializable
class PromotionTrait(val promotions: List<Piece>) : MoveTrait {
    override val type get() = MoveTraitType.PROMOTION

    var promotion: Piece? = null

    override val shouldComeBefore get() = setOf(MoveTraitType.TARGET)

    override fun execute(env: MoveEnvironment, move: Move) {
        val promotion = promotion!!
        if (promotion !in promotions) throw NoSuchElementException("Promotion not valid: $promotion")
        move.pieceTracker.traceMove(env, move.main.boardPiece().promote(promotion))
    }

    override fun undo(env: MoveEnvironment, move: Move) {
        move.pieceTracker.traceMoveBack(env, move.main)
    }
}

val Move.promotionTrait get() = get(MoveTraitType.PROMOTION)

@Serializable
class RequireFlagTrait(val flags: Map<Pos, Set<ChessFlag>>) : MoveTrait {
    override val type get() = MoveTraitType.REQUIRE_FLAG
}

val Move.requireFlagTrait get() = get(MoveTraitType.REQUIRE_FLAG)

@Serializable
class FlagTrait(val flags: Map<Pos, Map<ChessFlag, Int>>) : MoveTrait {
    override val type get() = MoveTraitType.FLAG

    override fun execute(env: MoveEnvironment, move: Move) {
        for ((p, f) in flags)
            for ((t, a) in f)
                env.board[p, t] = a
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

    private fun checkForChecks(color: Color, env: MoveEnvironment): CheckType? {
        env.updateMoves()
        val pieces = env.board.piecesOf(!color)
        val inCheck = env.variant.isInCheck(env.board, !color)
        val noMoves = pieces.all { it.getMoves(env.board).none { m -> env.variant.isLegal(m, env.board) } }
        return when {
            inCheck && noMoves -> CheckType.CHECKMATE
            inCheck -> CheckType.CHECK
            else -> null
        }
    }

    override fun execute(env: MoveEnvironment, move: Move) {
        checkType = checkForChecks(move.main.color, env)
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

    override fun execute(env: MoveEnvironment, move: Move) {
        env.board[capture]?.let {
            move.pieceTracker.giveName("capture", it)
            move.pieceTracker.traceMove(env, move.toCapture.capture(by ?: move.main.color))
            captureSuccess = true
        }
    }

    override fun undo(env: MoveEnvironment, move: Move) {
        if (captureSuccess)
            move.pieceTracker.traceMoveBack(env, move.captured)
    }
}

val Move.captureTrait get() = get(MoveTraitType.CAPTURE)

@Serializable
class TargetTrait(val target: Pos) : MoveTrait {
    override val type get() = MoveTraitType.TARGET

    var uniquenessCoordinate: UniquenessCoordinate = UniquenessCoordinate()
        private set

    override val shouldComeBefore get() = setOf(MoveTraitType.CAPTURE)

    private fun getUniquenessCoordinate(piece: BoardPiece, target: Pos, env: MoveEnvironment): UniquenessCoordinate {
        val pieces = env.board.pieces.filter { it.color == piece.color && it.type == piece.type }
        val consideredPieces = pieces.filter { p ->
            p.getLegalMoves(env.board).any { it.targetTrait?.target == target }
        }
        return when {
            consideredPieces.size == 1 -> UniquenessCoordinate()
            consideredPieces.count { it.pos.file == piece.pos.file } == 1 -> UniquenessCoordinate(file = piece.pos.file)
            consideredPieces.count { it.pos.rank == piece.pos.rank } == 1 -> UniquenessCoordinate(rank = piece.pos.rank)
            else -> UniquenessCoordinate(piece.pos)
        }
    }

    override fun execute(env: MoveEnvironment, move: Move) {
        uniquenessCoordinate = getUniquenessCoordinate(move.main.boardPiece(), target, env)
        move.pieceTracker.traceMove(env, move.main.boardPiece().move(target))
    }

    override fun undo(env: MoveEnvironment, move: Move) {
        move.pieceTracker.traceMoveBack(env, move.main)
    }
}

val Move.targetTrait get() = get(MoveTraitType.TARGET)

@Serializable
class SpawnTrait(val piece: BoardPiece) : MoveTrait {
    override val type get() = MoveTraitType.SPAWN

    override val shouldComeBefore get() = setOf(MoveTraitType.TARGET, MoveTraitType.CAPTURE)

    override fun execute(env: MoveEnvironment, move: Move) {
        env.callEvent(env.board.createSpawnEvent(piece))
    }

    override fun undo(env: MoveEnvironment, move: Move) {
        env.callEvent(env.board.createClearEvent(piece))
    }

}

val Move.spawnTrait get() = get(MoveTraitType.SPAWN)

@Serializable
class ClearTrait(val piece: BoardPiece) : MoveTrait {
    override val type get() = MoveTraitType.CLEAR

    override val shouldComeAfter get() = setOf(MoveTraitType.TARGET)

    override fun execute(env: MoveEnvironment, move: Move) {
        env.callEvent(env.board.createClearEvent(piece))
    }

    override fun undo(env: MoveEnvironment, move: Move) {
        env.callEvent(env.board.createSpawnEvent(piece))
    }

}

val Move.clearTrait get() = get(MoveTraitType.CLEAR)