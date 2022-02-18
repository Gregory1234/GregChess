package gregc.gregchess.chess.move

import gregc.gregchess.chess.ChessGame
import gregc.gregchess.chess.Pos
import gregc.gregchess.chess.piece.PlacedPiece
import gregc.gregchess.chess.piece.boardPiece
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable
data class Move(
    val pieceTracker: PieceTracker, val display: Pos,
    val stopBlocking: Set<Pos> = emptySet(), val startBlocking: Set<Pos> = emptySet(),
    val neededEmpty: Set<Pos> = emptySet(), val passedThrough: Set<Pos> = emptySet(),
    val isPhantomMove: Boolean = false, val traits: List<MoveTrait> = emptyList()
) {
    val origin: Pos get() = pieceTracker.getOriginal("main").boardPiece().pos
    val main: PlacedPiece get() = pieceTracker["main"]

    fun <T : MoveTrait> getTrait(cl: KClass<T>): T? = traits.filterIsInstance(cl.java).firstOrNull()
    inline fun <reified T : MoveTrait> getTrait(): T? = getTrait(T::class)

    fun setup(game: ChessGame) {
        traits.forEach { it.setup(game, this) }
    }

    fun execute(game: ChessGame) {
        val completedTraits = mutableListOf<MoveTrait>()
        val remainingTraits = traits.toMutableList()
        for (pass in 0u..255u) {
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
                        mt.execute(game, this)
                        completedTraits += mt
                        true
                    } catch(e: Throwable) {
                        for(t in completedTraits.asReversed())
                            t.undo(game, this)
                        game.board.updateMoves()
                        throw TraitsCouldNotExecuteException(remainingTraits, e)
                    }
                }
            }
            if (remainingTraits.isEmpty()) {
                game.board.updateMoves()
                return
            }
        }

        for(t in completedTraits.asReversed())
            t.undo(game, this)
        game.board.updateMoves()
        throw TraitsCouldNotExecuteException(remainingTraits)
    }

    val name: MoveName get() = MoveName(traits.map { it.nameTokens })

    fun undo(game: ChessGame) {
        val completedTraits = mutableListOf<MoveTrait>()
        val remainingTraits = traits.toMutableList()
        for (pass in 0u..255u) {
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
                        mt.undo(game, this)
                        completedTraits += mt
                        true
                    } catch(e: Throwable) {
                        for(t in completedTraits.asReversed())
                            t.execute(game, this)
                        game.board.updateMoves()
                        throw TraitsCouldNotExecuteException(remainingTraits, e)
                    }
                }
            }
            if (remainingTraits.isEmpty()) {
                game.board.updateMoves()
                return
            }
        }
        for(t in completedTraits.asReversed())
            t.execute(game, this)
        game.board.updateMoves()
        throw TraitsCouldNotExecuteException(remainingTraits)
    }
}

