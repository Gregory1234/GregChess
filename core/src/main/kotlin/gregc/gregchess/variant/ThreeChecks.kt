package gregc.gregchess.variant

import gregc.gregchess.*
import gregc.gregchess.board.ChessboardView
import gregc.gregchess.board.boardView
import gregc.gregchess.match.*
import gregc.gregchess.move.Move
import gregc.gregchess.move.MoveEnvironment
import gregc.gregchess.move.trait.MoveTrait
import gregc.gregchess.move.trait.MoveTraitType
import gregc.gregchess.piece.BoardPiece
import gregc.gregchess.results.*
import kotlinx.serialization.*

object ThreeChecks : ChessVariant(), Registering {

    @Serializable
    class CheckCounter private constructor(
        val limit: Int,
        @SerialName("checks") internal val checks_: MutableByColor<Int>
    ) : Component {
        constructor(limit: Int) : this(limit, mutableByColor(0))

        override val type get() = CHECK_COUNTER

        @Transient
        private lateinit var match: ChessMatch

        override fun init(match: ChessMatch) {
            this.match = match
        }

        val check: ByColor<Int> get() = byColor { checks_[it] }

        fun registerCheck(color: Color) {
            checks_[color]++
        }

        fun removeCheck(color: Color) {
            checks_[color]--
        }

        fun checkForMatchEnd() {
            for ((s, c) in checks_.toIndexedList())
                if (c >= limit)
                    match.stop(s.lostBy(CHECK_LIMIT, limit.toString()))
        }

        operator fun get(s: Color) = checks_[s]
    }

    @Serializable
    class CheckCounterTrait : MoveTrait {
        override val type get() = CHECK_COUNTER_TRAIT

        override val shouldComeLast: Boolean = true

        var checkRegistered: Boolean = false
            private set

        override fun execute(env: MoveEnvironment, move: Move) {
            env.updateMoves()
            if (env.variant.isInCheck(env.boardView, !move.main.color)) {
                env.require(CHECK_COUNTER).registerCheck(!move.main.color)
                checkRegistered = true
            }
        }

        override fun undo(env: MoveEnvironment, move: Move) {
            if (checkRegistered) {
                env.require(CHECK_COUNTER).removeCheck(!move.main.color)
            }
        }
    }

    @JvmField
    @Register
    val CHECK_LIMIT = DetEndReason(EndReason.Type.NORMAL)

    @JvmField
    @Register("check_counter")
    val CHECK_COUNTER_TRAIT = MoveTraitType(CheckCounterTrait.serializer())

    @JvmField
    @Register
    val CHECK_COUNTER = ComponentType(CheckCounter::class)

    override fun getPieceMoves(piece: BoardPiece, board: ChessboardView): List<Move> =
        Normal.getPieceMoves(piece, board).map {
            it.copy(traits = it.traits + CheckCounterTrait())
        }

    override fun checkForMatchEnd(match: ChessMatch) {
        match.require(CHECK_COUNTER).checkForMatchEnd()

        Normal.checkForMatchEnd(match)
    }

    override val requiredComponents = setOf(CHECK_COUNTER)

}