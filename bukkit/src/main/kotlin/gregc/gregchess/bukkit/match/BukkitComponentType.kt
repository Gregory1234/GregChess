package gregc.gregchess.bukkit.match

import gregc.gregchess.RegisterAll
import gregc.gregchess.bukkit.properties.BukkitGregChessAdapter
import gregc.gregchess.bukkit.properties.ScoreboardManager
import gregc.gregchess.bukkit.renderer.BukkitRenderer
import gregc.gregchess.match.ComponentType

@RegisterAll(ComponentType::class)
object BukkitComponentType {
    @JvmField
    val ADAPTER = ComponentType(BukkitGregChessAdapter::class)
    @JvmField
    val RENDERER = ComponentType(BukkitRenderer::class)
    @JvmField
    val MATCH_CONTROLLER = ComponentType(MatchController::class)
    @JvmField
    val SCOREBOARD_MANAGER = ComponentType(ScoreboardManager::class)
    @JvmField
    val SPECTATOR_MANAGER = ComponentType(SpectatorManager::class)
}