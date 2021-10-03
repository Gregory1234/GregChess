package gregc.gregchess.fabric.chess.component

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.SimpleComponent
import gregc.gregchess.fabric.chess.ChessGameManager
import gregc.gregchess.fabric.chess.forEachUnique
import gregc.gregchess.fabric.coroutines.FabricScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlin.coroutines.CoroutineContext

class PlayerManager(
    game: ChessGame,
) : SimpleComponent(game), CoroutineScope {

    override val coroutineContext: CoroutineContext = FabricScope().coroutineContext

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
                players.forEach(ChessPlayer::stop)
                ChessGameManager -= game
                cancel()
            }
            else -> {
            }
        }
    }
}