package gregc.gregchess.fabric.game

import gregc.gregchess.fabric.player.*
import gregc.gregchess.fabric.renderer.server
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
                game.sides.forEachUnique(FabricChessSide::sendStartMessage)
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