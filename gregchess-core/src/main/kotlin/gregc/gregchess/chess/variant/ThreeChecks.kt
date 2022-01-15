package gregc.gregchess.chess.variant

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.*
import gregc.gregchess.chess.move.*
import gregc.gregchess.chess.piece.BoardPiece
import gregc.gregchess.registry.Register
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

object ThreeChecks : ChessVariant() {

    @Serializable
    data class CheckCounterData(val limit: UInt, val checks: ByColor<UInt> = byColor(0u)) : ComponentData<CheckCounter> {
        override val componentClass: KClass<out CheckCounter> get() = CheckCounter::class

        override fun getComponent(game: ChessGame) = CheckCounter(game, this)
    }

    class CheckCounter(game: ChessGame, data: CheckCounterData) : Component(game) {

        private val checks = mutableByColor { data.checks[it] }
        private val limit = data.limit

        override val data get() = CheckCounterData(limit, byColor { checks[it] })

        fun registerCheck(color: Color) {
            checks[color]++
        }

        fun removeCheck(color: Color) {
            checks[color]--
        }

        fun checkForGameEnd() {
            for ((s, c) in checks.toIndexedList())
                if (c >= limit)
                    game.stop(s.lostBy(CHECK_LIMIT, limit.toString()))
        }

        operator fun get(s: Color) = checks[s]
    }

    @Serializable
    class CheckCounterTrait : MoveTrait {
        override val type get() = CHECK_COUNTER_TRAIT

        override val shouldComeLast: Boolean = true

        override val nameTokens: MoveName = MoveName(emptyMap())

        var checkRegistered: Boolean = false
            private set

        override fun execute(game: ChessGame, move: Move) {
            game.board.updateMoves()
            if (game.variant.isInCheck(game, !move.main.color)) {
                game.requireComponent<CheckCounter>().registerCheck(!move.main.color)
                checkRegistered = true
            }
        }

        override fun undo(game: ChessGame, move: Move) {
            if (checkRegistered) {
                game.requireComponent<CheckCounter>().removeCheck(!move.main.color)
            }
        }
    }

    @JvmField
    @Register
    val CHECK_LIMIT = DetEndReason(EndReason.Type.NORMAL)

    @JvmField
    @Register("check_counter")
    val CHECK_COUNTER_TRAIT = MoveTraitType(CheckCounterTrait::class)

    override fun getPieceMoves(piece: BoardPiece, board: Chessboard): List<Move> =
        Normal.getPieceMoves(piece, board).map {
            it.copy(traits = it.traits + CheckCounterTrait())
        }

    override fun checkForGameEnd(game: ChessGame) {
        game.requireComponent<CheckCounter>().checkForGameEnd()

        Normal.checkForGameEnd(game)
    }

    override val requiredComponents: Set<KClass<out Component>> = setOf(CheckCounter::class)

}