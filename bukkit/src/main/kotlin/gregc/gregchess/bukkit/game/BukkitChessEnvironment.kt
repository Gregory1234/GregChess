package gregc.gregchess.bukkit.game

import gregc.gregchess.bukkit.GregChessPlugin
import gregc.gregchess.bukkit.config
import gregc.gregchess.bukkitutils.coroutines.BukkitContext
import gregc.gregchess.bukkitutils.coroutines.BukkitDispatcher
import gregc.gregchess.game.ChessEnvironment
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