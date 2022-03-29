package gregc.gregchess.move

import gregc.gregchess.move.trait.*
import gregc.gregchess.piece.PlacedPiece
import gregc.gregchess.piece.boardPiece
import gregc.gregchess.util.Pos
import kotlinx.serialization.Serializable

@Serializable
data class Move(
    val pieceTracker: PieceTracker, val display: Pos,
    val stopBlocking: Set<Pos> = emptySet(), val startBlocking: Set<Pos> = emptySet(),
    val neededEmpty: Set<Pos> = emptySet(), val passedThrough: Set<Pos> = emptySet(),
    val isPhantomMove: Boolean = false, val traits: List<MoveTrait> = emptyList()
) {
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
                        env.updateMoves()
                        throw TraitsCouldNotExecuteException(remainingTraits, e)
                    }
                }
            }
            if (remainingTraits.isEmpty()) {
                env.updateMoves()
                return
            }
        }

        for(t in completedTraits.asReversed())
            t.undo(env, this)
        env.updateMoves()
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
                        env.updateMoves()
                        throw TraitsCouldNotExecuteException(remainingTraits, e)
                    }
                }
            }
            if (remainingTraits.isEmpty()) {
                env.updateMoves()
                return
            }
        }
        for(t in completedTraits.asReversed())
            t.execute(env, this)
        env.updateMoves()
        throw TraitsCouldNotExecuteException(remainingTraits)
    }
}

