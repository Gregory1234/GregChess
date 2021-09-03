package gregc.gregchess.chess.variant

import gregc.gregchess.GregChessModule
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Component
import gregc.gregchess.chess.component.ComponentData
import gregc.gregchess.register
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

object ThreeChecks : ChessVariant() {

    @Serializable
    data class CheckCounterData(val limit: UInt, val checks: BySides<UInt> = bySides(0u)) : ComponentData<CheckCounter> {
        override fun getComponent(game: ChessGame) = CheckCounter(game, this)
    }

    class CheckCounter(game: ChessGame, data: CheckCounterData) : Component(game) {

        private val checks = mutableBySides { data.checks[it] }
        private val limit = data.limit

        override val data get() = CheckCounterData(limit, bySides { checks[it] })

        fun registerCheck(side: Side) {
            checks[side]++
        }

        fun removeCheck(side: Side) {
            checks[side]--
        }

        fun checkForGameEnd() {
            for ((s, c) in checks.toIndexedList())
                if (c >= limit)
                    game.stop(s.lostBy(CHECK_LIMIT, limit.toString()))
        }

        operator fun get(s: Side) = checks[s]
    }

    @Serializable
    class CheckCounterTrait(var checkRegistered: Boolean = false): MoveTrait {
        override val nameTokens: MoveName = MoveName()

        override fun execute(game: ChessGame, move: Move, pass: UByte, remaining: List<MoveTrait>): Boolean {
            if (remaining.all { it is CheckTrait || it is CheckCounterTrait }) {
                game.board.updateMoves()
                if (game.variant.isInCheck(game, !move.piece.side)) {
                    game.requireComponent<CheckCounter>().registerCheck(!move.piece.side)
                    checkRegistered = true
                }
                return true
            }
            return false
        }

        override fun undo(game: ChessGame, move: Move, pass: UByte, remaining: List<MoveTrait>): Boolean {
            if (checkRegistered) {
                game.requireComponent<CheckCounter>().removeCheck(!move.piece.side)
            }
            return true
        }
    }

    @JvmField
    val CHECK_LIMIT = GregChessModule.register("check_limit", DetEndReason(EndReason.Type.NORMAL))

    override fun getPieceMoves(piece: BoardPiece): List<Move> = Normal.getPieceMoves(piece).map {
        it.copy(traits = it.traits + CheckCounterTrait())
    }

    override fun checkForGameEnd(game: ChessGame) {
        game.requireComponent<CheckCounter>().checkForGameEnd()
        Normal.checkForGameEnd(game)
    }

    override val requiredComponents: Set<KClass<out Component>> = setOf(CheckCounter::class)

}