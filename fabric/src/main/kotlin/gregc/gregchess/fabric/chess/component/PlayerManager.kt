package gregc.gregchess.fabric.chess.component

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Component
import gregc.gregchess.chess.component.ComponentData
import gregc.gregchess.fabric.chess.ChessGameManager
import gregc.gregchess.fabric.chess.forEachUnique
import kotlinx.serialization.Serializable

@Serializable
object PlayerManagerData : ComponentData<PlayerManager> {
    override fun getComponent(game: ChessGame) = PlayerManager(game, this)
}

class PlayerManager(game: ChessGame, override val data: PlayerManagerData) : Component(game) {


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
                    //it.player.showGameResults(it.side, results!!)
                }
                players.forEach(ChessPlayer::stop)
                ChessGameManager -= game
            }
            else -> {
            }
        }
    }
}