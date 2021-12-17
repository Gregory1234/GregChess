package gregc.gregchess.bukkit.coroutines

import gregc.gregchess.bukkit.GregChess
import gregc.gregchess.chess.ChessEnvironment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.Serializable

@Serializable
object BukkitChessEnvironment : ChessEnvironment {
    override val pgnSite: String get() = "GregChess Bukkit plugin"
    override val coroutineDispatcher: CoroutineDispatcher = BukkitDispatcher(GregChess.plugin, BukkitContext.SYNC)
}