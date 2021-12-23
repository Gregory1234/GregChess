package gregc.gregchess.fabric.chess.component

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.SimpleComponent
import gregc.gregchess.chess.player.ChessSide
import gregc.gregchess.fabric.chess.ChessGameManager
import gregc.gregchess.fabric.chess.player.forEachUnique
import gregc.gregchess.fabric.chess.player.showGameResults
import kotlinx.coroutines.cancel

class GameController(game: ChessGame) : SimpleComponent(game) {

    @ChessEventHandler
    fun handleEvents(e: GameBaseEvent) = with(game) {
        when (e) {
            GameBaseEvent.START -> {
                ChessGameManager += game
                sides.forEachUnique { it.init() }
            }
            GameBaseEvent.STOP -> {
                sides.forEachUnique {
                    it.player.getServerPlayer(game.server)?.showGameResults(it.color, results!!)
                }
                sides.forEach(ChessSide<*>::stop)
                ChessGameManager -= game
                game.coroutineScope.cancel()
            }
            else -> {
            }
        }
    }
}