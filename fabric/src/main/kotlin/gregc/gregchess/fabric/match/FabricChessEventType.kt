package gregc.gregchess.fabric.match

import gregc.gregchess.fabric.player.PiecePlayerActionEvent
import gregc.gregchess.match.ChessEventType

object FabricChessEventType {
    @JvmField
    val PIECE_PLAYER_ACTION = ChessEventType<PiecePlayerActionEvent>()
}