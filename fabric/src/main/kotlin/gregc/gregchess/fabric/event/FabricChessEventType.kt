package gregc.gregchess.fabric.event

import gregc.gregchess.event.ChessEventType
import gregc.gregchess.fabric.player.PiecePlayerActionEvent

object FabricChessEventType {
    @JvmField
    val PIECE_PLAYER_ACTION = ChessEventType<PiecePlayerActionEvent>()
}