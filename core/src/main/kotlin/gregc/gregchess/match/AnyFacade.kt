package gregc.gregchess.match

import gregc.gregchess.event.ChessEvent
import gregc.gregchess.event.ChessEventCaller

interface AnyFacade : ChessEventCaller {
    val match: ChessMatch
    override fun callEvent(event: ChessEvent) = match.callEvent(event)
}