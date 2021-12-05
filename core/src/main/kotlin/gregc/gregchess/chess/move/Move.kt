package gregc.gregchess.chess.move

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Chessboard
import gregc.gregchess.chess.piece.BoardPiece
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

// TODO: add a way to keep track of moving pieces
@Serializable
data class Move(
    val piece: BoardPiece, val display: Pos, val floor: Floor,
    val stopBlocking: Set<Pos>, val startBlocking: Set<Pos>,
    val neededEmpty: Set<Pos>, val passedThrough: Set<Pos>,
    val flagsNeeded: Set<Pair<Pos, ChessFlagType>>, val traits: List<MoveTrait>
) {
    fun <T : MoveTrait> getTrait(cl: KClass<T>): T? = traits.filterIsInstance(cl.java).firstOrNull()
    inline fun <reified T : MoveTrait> getTrait(): T? = getTrait(T::class)

    fun setup(game: ChessGame) {
        traits.forEach { it.setup(game, this) }
    }

    fun execute(game: ChessGame) {
        var remainingTraits = traits
        for (pass in 0u..255u) {
            remainingTraits = remainingTraits.filterNot { mt ->
                if (remainingTraits.any { it::class in mt.shouldComeBefore })
                    false
                else if (remainingTraits.any { mt::class in it.shouldComeAfter })
                    false
                else if (!mt.shouldComeFirst && remainingTraits.any { it.shouldComeFirst })
                    false
                else if (mt.shouldComeLast && remainingTraits.any { !it.shouldComeLast })
                    false
                else {
                    mt.execute(game, this)
                    true
                }
            }
            if (remainingTraits.isEmpty()) return
        }
        throw TraitsCouldNotExecuteException(remainingTraits)
    }

    val name: MoveName get() = MoveName(traits.map { it.nameTokens })

    fun undo(game: ChessGame) {
        var remainingTraits = traits
        for (pass in 0u..255u) {
            remainingTraits = remainingTraits.filterNot { mt ->
                if (remainingTraits.any { it::class in mt.shouldComeAfter })
                    false
                else if (remainingTraits.any { mt::class in it.shouldComeBefore })
                    false
                else if (!mt.shouldComeLast && remainingTraits.any { it.shouldComeLast })
                    false
                else if (mt.shouldComeFirst && remainingTraits.any { !it.shouldComeFirst })
                    false
                else {
                    mt.undo(game, this)
                    true
                }
            }
            if (remainingTraits.isEmpty()) {
                return
            }
        }
        throw TraitsCouldNotExecuteException(remainingTraits)
    }

    fun show(board: Chessboard) {
        board[display]?.moveMarker = floor
    }

    fun hide(board: Chessboard) {
        board[display]?.moveMarker = null
    }

    fun showDone(board: Chessboard) {
        board[piece.pos]?.previousMoveMarker = Floor.LAST_START
        board[display]?.moveMarker = Floor.LAST_END
    }

    fun hideDone(board: Chessboard) {
        board[piece.pos]?.previousMoveMarker = null
        board[display]?.moveMarker = null
    }
}

