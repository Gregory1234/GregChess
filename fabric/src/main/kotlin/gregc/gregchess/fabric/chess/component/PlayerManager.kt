package gregc.gregchess.fabric.chess.component

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Component
import gregc.gregchess.fabric.chess.*

class PlayerManager(private val game: ChessGame) : Component {
    object Settings : Component.Settings<PlayerManager> {
        override fun getComponent(game: ChessGame) = PlayerManager(game)
    }

    @ChessEventHandler
    fun handleEvents(e: GameBaseEvent) = with(game) {
        when (e) {
            GameBaseEvent.START -> {
                ChessGameManager += game
                players.forEachUnique { it.init() }
                variant.start(game)
            }
            GameBaseEvent.STOP -> {
                players.forEachUnique {
                    it.player.showGameResults(it.side, results!!)
                }
                players.forEach(ChessPlayer::stop)
                finishStopping()
            }
            GameBaseEvent.STOPPED -> {
                ChessGameManager -= game
            }
            else -> {
            }
        }
    }
}