package gregc.gregchess.chess.variant

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Chessboard
import gregc.gregchess.chess.component.Component
import gregc.gregchess.chess.move.*
import gregc.gregchess.chess.piece.BoardPiece
import gregc.gregchess.registry.Register
import kotlinx.serialization.*
import kotlin.reflect.KClass

object ThreeChecks : ChessVariant() {

    @Serializable
    class CheckCounter private constructor(
        val limit: UInt,
        @SerialName("checks") internal val checks_: MutableByColor<UInt>
    ) : Component {
        constructor(limit: UInt) : this(limit, mutableByColor(0u))

        @Transient
        private lateinit var game: ChessGame

        override fun init(game: ChessGame) {
            this.game = game
        }

        val check: ByColor<UInt> get() = byColor { checks_[it] }

        fun registerCheck(color: Color) {
            checks_[color]++
        }

        fun removeCheck(color: Color) {
            checks_[color]--
        }

        fun checkForGameEnd() {
            for ((s, c) in checks_.toIndexedList())
                if (c >= limit)
                    game.stop(s.lostBy(CHECK_LIMIT, limit.toString()))
        }

        operator fun get(s: Color) = checks_[s]
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