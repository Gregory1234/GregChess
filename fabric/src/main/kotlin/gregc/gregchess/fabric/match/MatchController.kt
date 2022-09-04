package gregc.gregchess.fabric.match

import gregc.gregchess.fabric.player.*
import gregc.gregchess.match.*
import kotlinx.serialization.Serializable

@Serializable
object MatchController : Component {

    override val type get() = FabricComponentType.MATCH_CONTROLLER

    override fun init(match: ChessMatch, events: ChessEventRegistry) {
        events.register(ChessEventType.BASE) { handleBaseEvent(match, it) }
    }

    private fun handleBaseEvent(match: ChessMatch, e: ChessBaseEvent) = with(match) {
        when (e) {
            ChessBaseEvent.START -> {
                ChessMatchManager += match
                match.sideFacades.forEachUnique(FabricChessSideFacade::sendStartMessage)
            }
            ChessBaseEvent.STOP -> {
                sideFacades.forEachUniqueEntity { player, color ->
                    player.showMatchResults(color, results!!)
                }
                ChessMatchManager -= match
            }
            else -> {
            }
        }
    }
}