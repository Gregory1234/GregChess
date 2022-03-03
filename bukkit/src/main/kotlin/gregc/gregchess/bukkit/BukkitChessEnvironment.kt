package gregc.gregchess.bukkit

import gregc.gregchess.bukkitutils.coroutines.BukkitContext
import gregc.gregchess.bukkitutils.coroutines.BukkitDispatcher
import gregc.gregchess.chess.ChessEnvironment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.Serializable
import java.time.Clock
import java.time.ZoneId

@Serializable
object BukkitChessEnvironment : ChessEnvironment {
    override val pgnSite: String get() = "GregChess Bukkit plugin"
    override val coroutineDispatcher: CoroutineDispatcher = BukkitDispatcher(GregChessPlugin.plugin, BukkitContext.SYNC)
    override val clock: Clock get() = config.getString("TimeZone")?.let { Clock.system(ZoneId.of(it)) } ?: Clock.systemDefaultZone()
}