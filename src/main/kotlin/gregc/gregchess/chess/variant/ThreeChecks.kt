package gregc.gregchess.chess.variant

import gregc.gregchess.ConfigManager
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.*

object ThreeChecks : ChessVariant("ThreeChecks") {
    class CheckCounter(private val game: ChessGame) : Component {
        private var checks = MutableBySides(0,0)

        @GameEvent(GameBaseEvent.START)
        fun start() {
            game.scoreboard += object :
                PlayerProperty(ConfigManager.getString("Component.CheckCounter.CheckCounter")) {
                override fun invoke(s: Side): String = checks[s].toString()
            }
        }

        @GameEvent(GameBaseEvent.END_TURN)
        fun endTurn() {
            if (game.variant.isInCheck(game, !game.currentTurn))
                checks[!game.currentTurn]++
        }

        fun checkForGameEnd() {
            checks.forEachIndexed {s, c ->
                if (c >= 3)
                    game.stop(ThreeChecksEndReason(!s))
            }
        }
    }

    class ThreeChecksEndReason(winner: Side) :
        ChessGame.EndReason("Chess.EndReason.ThreeChecks", "normal", winner)

    override fun start(game: ChessGame) {
        game.registerComponent(CheckCounter(game))
    }

    override fun checkForGameEnd(game: ChessGame) {
        game.getComponent<CheckCounter>()?.checkForGameEnd()
        Normal.checkForGameEnd(game)
    }

}