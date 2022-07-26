package gregc.gregchess.fabric.match

import gregc.gregchess.fabric.player.*
import gregc.gregchess.fabric.renderer.server
import gregc.gregchess.match.*
import kotlinx.serialization.Serializable

@Serializable
object MatchController : Component {

    override val type get() = FabricComponentType.MATCH_CONTROLLER

    override fun init(match: ChessMatch, eventManager: ChessEventManager) {
        eventManager.registerEvent(ChessEventType.BASE) { handleBaseEvent(match, it) }
    }

    private fun handleBaseEvent(match: ChessMatch, e: ChessBaseEvent) = with(match) {
        when (e) {
            ChessBaseEvent.START -> {
                ChessMatchManager += match
                match.sides.forEachUnique(FabricChessSide::sendStartMessage)
            }
            ChessBaseEvent.STOP -> {
                sides.forEachUniqueEntity(match.server) { player, color ->
                    player.showMatchResults(color, results!!)
                }
                ChessMatchManager -= match
            }
            else -> {
            }
        }
    }
}