package gregc.gregchess.fabric.chess.component

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.SimpleComponent
import gregc.gregchess.chess.player.ChessPlayer
import gregc.gregchess.fabric.chess.ChessGameManager
import gregc.gregchess.fabric.chess.player.forEachUnique
import kotlinx.coroutines.cancel

class GameController(game: ChessGame) : SimpleComponent(game) {

    @ChessEventHandler
    fun handleEvents(e: GameBaseEvent) = with(game) {
        when (e) {
            GameBaseEvent.START -> {
                ChessGameManager += game
                players.forEachUnique { it.init() }
            }
            GameBaseEvent.STOP -> {
                players.forEachUnique {
                    //it.player.showGameResults(it.color, results!!)
                }
                players.forEach(ChessPlayer<*>::stop)
                ChessGameManager -= game
                game.coroutineScope.cancel()
            }
            else -> {
            }
        }
    }
}