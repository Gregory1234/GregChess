package gregc.gregchess.variant

import gregc.gregchess.*
import gregc.gregchess.match.*
import gregc.gregchess.move.Move
import gregc.gregchess.move.MoveEnvironment
import gregc.gregchess.move.connector.*
import gregc.gregchess.move.trait.MoveTrait
import gregc.gregchess.move.trait.MoveTraitType
import gregc.gregchess.piece.BoardPiece
import gregc.gregchess.piece.PlacedPieceType
import gregc.gregchess.results.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object ThreeChecks : ChessVariant(), Registering {

    interface CheckCounterConnector : MoveConnector {
        val limit: Int

        val checks: ByColor<Int> get() = byColor(::get)
        operator fun get(color: Color): Int
        fun registerCheck(color: Color)
        fun removeCheck(color: Color)
        override val holders: Map<PlacedPieceType<*>, PieceHolder<*>> get() = emptyMap()
    }

    @Serializable
    class CheckCounter private constructor(
        override val limit: Int,
        @SerialName("checks") private val checks_: MutableByColor<Int>
    ) : Component, CheckCounterConnector {
        constructor(limit: Int) : this(limit, mutableByColor(0))

        override fun init(match: ChessMatch, eventManager: ChessEventManager) {
            eventManager.registerEvent(ChessEventType.ADD_MOVE_CONNECTORS) { e ->
                e[CHECK_COUNTER_CONNECTOR] = this
            }
            eventManager.registerEvent(ChessEventType.ADD_FAKE_MOVE_CONNECTORS) { e ->
                e[CHECK_COUNTER_CONNECTOR] = FakeCheckCounterConnector(limit, checks_)
            }
        }

        override val type get() = CHECK_COUNTER

        override fun registerCheck(color: Color) {
            checks_[color]++
        }

        override fun removeCheck(color: Color) {
            checks_[color]--
        }

        fun checkForMatchEnd(match: ChessMatch) {
            for ((s, c) in checks_.toIndexedList())
                if (c >= limit)
                    match.stop(s.lostBy(CHECK_LIMIT, limit.toString()))
        }

        override fun get(color: Color) = checks_[color]

        private class FakeCheckCounterConnector(override val limit: Int, private val checks_: MutableByColor<Int>): CheckCounterConnector {
            override fun registerCheck(color: Color) {
                checks_[color]++
            }

            override fun removeCheck(color: Color) {
                checks_[color]--
            }

            override fun get(color: Color) = checks_[color]
        }
    }

    @Serializable
    class CheckCounterTrait : MoveTrait {
        override val type get() = CHECK_COUNTER_TRAIT

        override val shouldComeLast: Boolean = true

        var checkRegistered: Boolean = false
            private set

        override fun execute(env: MoveEnvironment, move: Move) {
            env.board.updateMoves()
            if (env.variant.isInCheck(env.board, !move.main.color)) {
                env[CHECK_COUNTER_CONNECTOR].registerCheck(!move.main.color)
                checkRegistered = true
            }
        }

        override fun undo(env: MoveEnvironment, move: Move) {
            if (checkRegistered) {
                env[CHECK_COUNTER_CONNECTOR].removeCheck(!move.main.color)
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
    val CHECK_COUNTER_CONNECTOR = MoveConnectorType(CheckCounterConnector::class)

    @JvmField
    @Register
    val CHECK_COUNTER = ComponentType(CheckCounter::class)

    override fun getPieceMoves(piece: BoardPiece, board: ChessboardView, variantOptions: Long): List<Move> =
        Normal.getPieceMoves(piece, board, variantOptions).map {
            it.copy(traits = it.traits + CheckCounterTrait())
        }

    override fun checkForMatchEnd(match: ChessMatch) {
        match.require(CHECK_COUNTER).checkForMatchEnd(match)

        Normal.checkForMatchEnd(match)
    }

    override val requiredComponents = setOf(CHECK_COUNTER)

}