package gregc.gregchess.chess.variant

import gregc.gregchess.ConfigManager
import gregc.gregchess.chess.ChessGame
import gregc.gregchess.chess.Side
import gregc.gregchess.chess.component.Component
import gregc.gregchess.chess.component.GameBaseEvent
import gregc.gregchess.chess.component.GameEvent
import gregc.gregchess.chess.component.PlayerProperty

object ThreeChecks : ChessVariant("ThreeChecks") {
    class CheckCounter(private val game: ChessGame) : Component {
        private var whiteChecks = 0
        private var blackChecks = 0

        @GameEvent(GameBaseEvent.START)
        fun start() {
            game.scoreboard += object :
                PlayerProperty(ConfigManager.getString("Component.CheckCounter.CheckCounter")) {
                override fun invoke(s: Side): String = when (s) {
                    Side.WHITE -> whiteChecks
                    Side.BLACK -> blackChecks
                }.toString()
            }
        }

        @GameEvent(GameBaseEvent.END_TURN)
        fun endTurn() {
            if (game.variant.isInCheck(game, !game.currentTurn))
                when (!game.currentTurn) {
                    Side.WHITE -> {
                        whiteChecks++
                    }
                    Side.BLACK -> {
                        blackChecks++
                    }
                }
        }

        fun checkForGameEnd() {
            if (whiteChecks >= 3)
                game.stop(ThreeChecksEndReason(Side.BLACK))
            if (blackChecks >= 3)
                game.stop(ThreeChecksEndReason(Side.WHITE))
        }
    }

    class ThreeChecksEndReason(winner: Side) :
        ChessGame.EndReason("Chess.EndReason.ThreeChecks", "normal", winner)

    override fun start(game: ChessGame) {
        game.registerComponent(CheckCounter(game))
    }

    override fun checkForGameEnd(game: ChessGame) {
        game.getComponent(CheckCounter::class)?.checkForGameEnd()
        Normal.checkForGameEnd(game)
    }

}