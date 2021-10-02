package gregc.gregchess.fabric.chess.component

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Component
import gregc.gregchess.chess.component.ComponentData
import gregc.gregchess.fabric.chess.ChessGameManager
import gregc.gregchess.fabric.chess.forEachUnique
import gregc.gregchess.fabric.coroutines.FabricScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.serialization.Serializable

@Serializable
object PlayerManagerData : ComponentData<PlayerManager> {
    override fun getComponent(game: ChessGame) = PlayerManager(game, this)
}

class PlayerManager(
    game: ChessGame,
    override val data: PlayerManagerData,
    private val scope: CoroutineScope = FabricScope()
) : Component(game), CoroutineScope by scope {


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
                scope.cancel()
            }
            else -> {
            }
        }
    }
}