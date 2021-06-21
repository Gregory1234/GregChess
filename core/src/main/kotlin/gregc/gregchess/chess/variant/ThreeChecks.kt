package gregc.gregchess.chess.variant

import gregc.gregchess.*
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.*

object ThreeChecks : ChessVariant("ThreeChecks") {
    private val EndReasonConfig.threeChecks by EndReasonConfig
    private val View.checkCounter get() = getLocalizedString("CheckCounter")
    private val ComponentsConfig.checkCounter by ComponentsConfig

    class CheckCounter(private val game: ChessGame) : Component {
        object Settings : Component.Settings<CheckCounter> {
            override fun getComponent(game: ChessGame) = CheckCounter(game)
        }

        private var checks = MutableBySides(0)

        @GameEvent(GameBaseEvent.START)
        fun start() {
            game.scoreboard.player(Config.component.checkCounter.checkCounter) { checks[it].toString() }
        }

        @GameEvent(GameBaseEvent.END_TURN)
        fun endTurn() {
            if (game.variant.isInCheck(game, !game.currentTurn))
                checks[!game.currentTurn]++
        }

        fun checkForGameEnd() {
            checks.forEachIndexed { s, c ->
                if (c >= 3)
                    game.stop(ThreeChecksEndReason(!s))
            }
        }
    }

    class ThreeChecksEndReason(winner: Side) : EndReason(Config.endReason.threeChecks, "normal", winner)

    override fun start(game: ChessGame) {
        game.requireComponent<CheckCounter>()
    }

    override fun checkForGameEnd(game: ChessGame) {
        game.requireComponent<CheckCounter>().checkForGameEnd()
        Normal.checkForGameEnd(game)
    }

    override val extraComponents: Collection<Component.Settings<*>>
        get() = listOf(CheckCounter.Settings)

}