package gregc.gregchess.bukkit.component

import gregc.gregchess.RegisterAll
import gregc.gregchess.bukkit.properties.BukkitGregChessAdapter
import gregc.gregchess.bukkit.properties.ScoreboardManager
import gregc.gregchess.bukkit.renderer.BukkitRenderer
import gregc.gregchess.component.ComponentType

@RegisterAll(ComponentType::class)
object BukkitComponentType {
    @JvmField
    val ADAPTER = ComponentType(BukkitGregChessAdapter::class, BukkitGregChessAdapter.serializer())
    @JvmField
    val RENDERER = ComponentType(BukkitRenderer::class, BukkitRenderer.serializer())
    @JvmField
    val MATCH_CONTROLLER = ComponentType(MatchController::class, MatchController.serializer())
    @JvmField
    val SCOREBOARD_MANAGER = ComponentType(ScoreboardManager::class, ScoreboardManager.serializer())
    @JvmField
    val SPECTATOR_MANAGER = ComponentType(SpectatorManager::class, SpectatorManager.serializer())
}