package gregc.gregchess.bukkit.chess.component

import gregc.gregchess.chess.component.ComponentType
import gregc.gregchess.util.RegisterAll

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