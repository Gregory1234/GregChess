package gregc.gregchess.fabric.chess.component

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Component
import gregc.gregchess.chess.player.ChessSide
import gregc.gregchess.fabric.chess.ChessGameManager
import gregc.gregchess.fabric.chess.player.*
import kotlinx.coroutines.cancel
import kotlinx.serialization.Serializable

@Serializable
object GameController : Component {

    @ChessEventHandler
    fun handleEvents(game: ChessGame, e: GameBaseEvent) = with(game) {
        when (e) {
            GameBaseEvent.START -> {
                ChessGameManager += game
                sides.forEachUnique { it.init() }
            }
            GameBaseEvent.STOP -> {
                sides.forEachUniqueFabric(game.server) { player, color ->
                    player.showGameResults(color, results!!)
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