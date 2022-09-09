package gregc.gregchess.fabric.component

import gregc.gregchess.component.Component
import gregc.gregchess.event.*
import gregc.gregchess.fabric.match.ChessMatchManager
import gregc.gregchess.match.ChessMatch
import kotlinx.serialization.Serializable

@Serializable
object MatchController : Component {

    override val type get() = FabricComponentType.MATCH_CONTROLLER

    private fun onStart(match: ChessMatch) {
        ChessMatchManager += match
    }

    private fun onClear(match: ChessMatch) {
        ChessMatchManager -= match
    }

    override fun init(match: ChessMatch, events: ChessEventRegistry) {
        events.register(ChessEventType.BASE, ChessEventOrderConstraint(runBeforeAll = true)) {
            when (it) {
                ChessBaseEvent.START -> onStart(match)
                else -> {}
            }
        }
        events.register(ChessEventType.BASE, ChessEventOrderConstraint(runAfterAll = true)) {
            when (it) {
                ChessBaseEvent.CLEAR, ChessBaseEvent.PANIC -> onClear(match)
                else -> {}
            }
        }
    }
}