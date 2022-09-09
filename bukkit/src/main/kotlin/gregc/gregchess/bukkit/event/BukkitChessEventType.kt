package gregc.gregchess.bukkit.event

import gregc.gregchess.bukkit.match.MatchInfoEvent
import gregc.gregchess.bukkit.player.PiecePlayerActionEvent
import gregc.gregchess.bukkit.properties.AddPropertiesEvent
import gregc.gregchess.bukkit.renderer.ResetPlayerEvent
import gregc.gregchess.event.ChessEventType

object BukkitChessEventType {
    @JvmField
    val PLAYER = ChessEventType<PlayerEvent>()
    @JvmField
    val SPECTATOR = ChessEventType<SpectatorEvent>()
    @JvmField
    val ADD_PROPERTIES = ChessEventType<AddPropertiesEvent>()
    @JvmField
    val PIECE_PLAYER_ACTION = ChessEventType<PiecePlayerActionEvent>()
    @JvmField
    val RESET_PLAYER = ChessEventType<ResetPlayerEvent>()
    @JvmField
    val MATCH_INFO = ChessEventType<MatchInfoEvent>()
}