package gregc.gregchess.chess.variant

import gregc.gregchess.GregChessModule
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.*
import gregc.gregchess.register
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

object ThreeChecks : ChessVariant() {

    @Serializable
    data class CheckCounterData(val limit: UInt, val checks: ByColor<UInt> = byColor(0u)) :
        ComponentData<CheckCounter> {
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
    class CheckCounterTrait(var checkRegistered: Boolean = false) : MoveTrait {
        override val nameTokens: MoveName = nameOf()

        override fun execute(game: ChessGame, move: Move, remaining: List<MoveTrait>): Boolean {
            if (remaining.all { it is CheckTrait || it is CheckCounterTrait }) {
                game.board.updateMoves()
                if (game.variant.isInCheck(game, !move.piece.color)) {
                    game.requireComponent<CheckCounter>().registerCheck(!move.piece.color)
                    checkRegistered = true
                }
                return true
            }
            return false
        }

        override fun undo(game: ChessGame, move: Move, remaining: List<MoveTrait>): Boolean {
            if (checkRegistered) {
                game.requireComponent<CheckCounter>().removeCheck(!move.piece.color)
            }
            return true
        }
    }

    @JvmField
    val CHECK_LIMIT = GregChessModule.register("check_limit", DetEndReason(EndReason.Type.NORMAL))

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