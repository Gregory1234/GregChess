package gregc.gregchess.fabric.chess.component

import gregc.gregchess.chess.component.Component
import gregc.gregchess.fabric.chess.ChessGameManager
import gregc.gregchess.fabric.chess.player.forEachUniqueEntity
import gregc.gregchess.fabric.chess.player.showGameResults
import gregc.gregchess.game.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
class GameController : Component {

    override val type get() = FabricComponentType.GAME_CONTROLLER

    @Transient
    private lateinit var game: ChessGame

    override fun init(game: ChessGame) {
        this.game = game
    }

    @ChessEventHandler
    fun handleEvents(e: GameBaseEvent) = with(game) {
        when (e) {
            GameBaseEvent.START -> {
                ChessGameManager += game
            }
            GameBaseEvent.STOP -> {
                sides.forEachUniqueEntity(game.server) { player, color ->
                    player.showGameResults(color, results!!)
                }
                ChessGameManager -= game
            }
            else -> {
            }
        }
    }
}