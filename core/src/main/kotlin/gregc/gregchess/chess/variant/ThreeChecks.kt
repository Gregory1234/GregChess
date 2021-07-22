package gregc.gregchess.chess.variant

import gregc.gregchess.asIdent
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.AddPropertiesEvent
import gregc.gregchess.chess.component.Component
import kotlin.reflect.KClass

object ThreeChecks : ChessVariant("ThreeChecks") {

    class CheckCounter(private val game: ChessGame, private val limit: UInt) : Component {
        data class Settings(val limit: UInt) : Component.Settings<CheckCounter> {
            override fun getComponent(game: ChessGame) = CheckCounter(game, limit)
        }

        private var checks = MutableBySides(0u)

        @ChessEventHandler
        fun addProperties(e: AddPropertiesEvent) {
            e.player("check_counter".asIdent()) { checks[it] }
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
                    game.stop(CHECK_LIMIT.of(!s, limit))
            }
        }
    }

    private val CHECK_LIMIT = DetEndReason("check_limit".asIdent(), EndReason.Type.NORMAL)

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