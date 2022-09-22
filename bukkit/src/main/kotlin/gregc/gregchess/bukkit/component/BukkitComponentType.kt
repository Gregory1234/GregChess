package gregc.gregchess.bukkit.component

import gregc.gregchess.RegisterAll
import gregc.gregchess.bukkit.properties.BukkitGregChessAdapter
import gregc.gregchess.bukkit.properties.ScoreboardManager
import gregc.gregchess.bukkit.renderer.BukkitRenderer
import gregc.gregchess.component.ComponentType

@RegisterAll(ComponentType::class)
object BukkitComponentType {
    @JvmField
    val ADAPTER = ComponentType<BukkitGregChessAdapter>()
    @JvmField
    val RENDERER = ComponentType<BukkitRenderer>()
    @JvmField
    val MATCH_CONTROLLER = ComponentType<MatchController>()
    @JvmField
    val SCOREBOARD_MANAGER = ComponentType<ScoreboardManager>()
    @JvmField
    val SPECTATOR_MANAGER = ComponentType<SpectatorManager>()
}