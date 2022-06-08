package gregc.gregchess.fabric.match

import gregc.gregchess.fabric.player.*
import gregc.gregchess.fabric.renderer.server
import gregc.gregchess.match.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
class MatchController : Component {

    override val type get() = FabricComponentType.MATCH_CONTROLLER

    @Transient
    private lateinit var match: ChessMatch

    override fun init(match: ChessMatch) {
        this.match = match
    }

    @ChessEventHandler
    fun handleEvents(e: ChessBaseEvent) = with(match) {
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