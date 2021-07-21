package gregc.gregchess.chess.variant

import gregc.gregchess.asIdent
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Component
import gregc.gregchess.chess.component.player
import kotlin.reflect.KClass

object ThreeChecks : ChessVariant("ThreeChecks") {

    class CheckCounter(private val game: ChessGame, private val limit: UInt) : Component {
        data class Settings(val limit: UInt) : Component.Settings<CheckCounter> {
            override fun getComponent(game: ChessGame) = CheckCounter(game, limit)
        }

        private var checks = MutableBySides(0u)

        @ChessEventHandler
        fun init(e: GameBaseEvent) {
            if (e == GameBaseEvent.INIT)
                game.scoreboard.player("CheckCounter") { checks[it].toString() }
        }

        @ChessEventHandler
        fun endTurn(e: TurnEvent) {
            if (e == TurnEvent.END)
                if (game.variant.isInCheck(game, !game.currentTurn))
                    checks[!game.currentTurn]++
        }

        fun checkForGameEnd() {
            checks.forEachIndexed { s, c ->
                if (c >= limit)
                    game.stop(CheckLimitEndReason(!s, limit))
            }
        }
    }

    class CheckLimitEndReason(winner: Side, limit: UInt) : EndReason("check_limit".asIdent(), Type.NORMAL, winner, args = listOf(limit))

    override fun start(game: ChessGame) {
        game.requireComponent<CheckCounter>()
    }

    override fun checkForGameEnd(game: ChessGame) {
        game.requireComponent<CheckCounter>().checkForGameEnd()
        Normal.checkForGameEnd(game)
    }

    override val requiredComponents: Collection<KClass<out Component.Settings<*>>>
        get() = listOf(CheckCounter.Settings::class)

}