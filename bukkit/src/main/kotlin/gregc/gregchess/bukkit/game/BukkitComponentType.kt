package gregc.gregchess.bukkit.game

import gregc.gregchess.RegisterAll
import gregc.gregchess.bukkit.properties.BukkitGregChessAdapter
import gregc.gregchess.bukkit.properties.ScoreboardManager
import gregc.gregchess.bukkit.renderer.BukkitRenderer
import gregc.gregchess.game.ComponentType

@RegisterAll(ComponentType::class)
object BukkitComponentType {
    @JvmField
    val EVENT_RELAY = ComponentType(BukkitEventRelay::class)
    @JvmField
    val ADAPTER = ComponentType(BukkitGregChessAdapter::class)
    @JvmField
    val RENDERER = ComponentType(BukkitRenderer::class)
    @JvmField
    val GAME_CONTROLLER = ComponentType(GameController::class)
    @JvmField
    val SCOREBOARD_MANAGER = ComponentType(ScoreboardManager::class)
    @JvmField
    val SPECTATOR_MANAGER = ComponentType(SpectatorManager::class)
}