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
    data class CheckCounterData(
        val limit: UInt,
        val checks: MutableBySides<UInt> = mutableBySides(0u)
    ) : ComponentData<CheckCounter> {
        override fun getComponent(game: ChessGame) = CheckCounter(game, this)
    }

    class CheckCounter(game: ChessGame, override val data: CheckCounterData) : Component(game) {

        private val checks = data.checks
        private val limit = data.limit

        @ChessEventHandler
        fun endTurn(e: TurnEvent) {
            if (e == TurnEvent.END)
                if (game.variant.isInCheck(game, !game.currentTurn))
                    checks[!game.currentTurn]++
        }

        fun checkForGameEnd() {
            for ((s, c) in checks.toIndexedList())
                if (c >= limit)
                    game.stop(s.lostBy(CHECK_LIMIT, limit.toString()))
        }

        operator fun get(s: Side) = checks[s]
    }

    @JvmField
    val CHECK_LIMIT = GregChessModule.register("check_limit", DetEndReason(EndReason.Type.NORMAL))

    override fun start(game: ChessGame) {
        game.requireComponent<CheckCounter>()
    }

    override fun checkForGameEnd(game: ChessGame) {
        game.requireComponent<CheckCounter>().checkForGameEnd()
        Normal.checkForGameEnd(game)
    }

    override val requiredComponents: Collection<KClass<out ComponentData<*>>>
        get() = listOf(CheckCounterData::class)

}