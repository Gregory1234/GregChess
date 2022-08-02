package gregc.gregchess.move

import gregc.gregchess.Pos
import gregc.gregchess.move.connector.board
import gregc.gregchess.move.trait.MoveTrait
import gregc.gregchess.move.trait.MoveTraitType
import gregc.gregchess.piece.PlacedPiece
import gregc.gregchess.piece.boardPiece
import gregc.gregchess.registry.Registry
import kotlinx.serialization.Serializable

@Serializable
data class Move(
    val pieceTracker: PieceTracker, val display: Pos,
    val stopBlocking: Set<Pos> = emptySet(), val startBlocking: Set<Pos> = emptySet(),
    val neededEmpty: Set<Pos> = emptySet(), val passedThrough: Set<Pos> = emptySet(),
    val isPhantomMove: Boolean = false, val traits: List<MoveTrait> = emptyList()
) {

    class TraitsCouldNotExecuteException(traits: Collection<MoveTrait>, cause: Throwable? = null) :
        Exception(traits.toList().map { Registry.MOVE_TRAIT_TYPE[it.type] }.toString(), cause)

    val origin: Pos get() = pieceTracker.getOriginal("main").boardPiece().pos
    val main: PlacedPiece get() = pieceTracker["main"]

    @Suppress("UNCHECKED_CAST")
    operator fun <T : MoveTrait> get(type: MoveTraitType<T>): T? = traits.firstOrNull { it.type == type } as T?

    fun execute(env: MoveEnvironment) {
        val completedTraits = mutableListOf<MoveTrait>()
        val remainingTraits = traits.toMutableList()
        repeat(256) {
            remainingTraits.removeIf { mt ->
                if (remainingTraits.any { it.type in mt.shouldComeBefore })
                    false
                else if (remainingTraits.any { mt.type in it.shouldComeAfter })
                    false
                else if (!mt.shouldComeFirst && remainingTraits.any { it.shouldComeFirst })
                    false
                else if (mt.shouldComeLast && remainingTraits.any { !it.shouldComeLast })
                    false
                else {
                    try {
                        mt.execute(env, this)
                        completedTraits += mt
                        true
                    } catch(e: Throwable) {
                        for(t in completedTraits.asReversed())
                            t.undo(env, this)
                        env.board.updateMoves()
                        throw TraitsCouldNotExecuteException(remainingTraits, e)
                    }
                }
            }
            if (remainingTraits.isEmpty()) {
                env.board.updateMoves()
                return
            }
        }

        for(t in completedTraits.asReversed())
            t.undo(env, this)
        env.board.updateMoves()
        throw TraitsCouldNotExecuteException(remainingTraits)
    }

    fun undo(env: MoveEnvironment) {
        val completedTraits = mutableListOf<MoveTrait>()
        val remainingTraits = traits.toMutableList()
        repeat(256) {
            remainingTraits.removeIf { mt ->
                if (remainingTraits.any { it.type in mt.shouldComeAfter })
                    false
                else if (remainingTraits.any { mt.type in it.shouldComeBefore })
                    false
                else if (!mt.shouldComeLast && remainingTraits.any { it.shouldComeLast })
                    false
                else if (mt.shouldComeFirst && remainingTraits.any { !it.shouldComeFirst })
                    false
                else {
                    try {
                        mt.undo(env, this)
                        completedTraits += mt
                        true
                    } catch(e: Throwable) {
                        for(t in completedTraits.asReversed())
                            t.execute(env, this)
                        env.board.updateMoves()
                        throw TraitsCouldNotExecuteException(remainingTraits, e)
                    }
                }
            }
            if (remainingTraits.isEmpty()) {
                env.board.updateMoves()
                return
            }
        }
        for(t in completedTraits.asReversed())
            t.execute(env, this)
        env.board.updateMoves()
        throw TraitsCouldNotExecuteException(remainingTraits)
    }
}

