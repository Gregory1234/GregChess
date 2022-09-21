package gregc.gregchess.player

import gregc.gregchess.Color
import gregc.gregchess.event.ChessEvent
import gregc.gregchess.event.ChessEventType
import gregc.gregchess.match.ChessMatch
import gregc.gregchess.results.EndReason
import gregc.gregchess.results.drawBy

enum class HumanRequest(val onExecute: ChessMatch.() -> Unit) {
    DRAW({ stop(drawBy(EndReason.DRAW_AGREEMENT))}), UNDO({ board.undoLastMove() })
}

class HumanRequestEvent(val request: HumanRequest, val color: Color, val value: Boolean) : ChessEvent {
    override val type get() = ChessEventType.HUMAN_REQUEST
}

// TODO: add a way of checking for the same person
interface HumanChessSide : ChessSide {
    fun isRequesting(request: HumanRequest): Boolean
    fun toggleRequest(match: ChessMatch, request: HumanRequest)
    fun clearRequest(request: HumanRequest)
    // TODO: add a PlayerStatsSink
}

abstract class HumanChessSideFacade<T : HumanChessSide>(match: ChessMatch, side: T) : ChessSideFacade<T>(match, side) {
    fun isRequesting(request: HumanRequest): Boolean = side.isRequesting(request)
    fun toggleRequest(request: HumanRequest) = side.toggleRequest(match, request)
    fun clearRequest(request: HumanRequest) = side.clearRequest(request)
}