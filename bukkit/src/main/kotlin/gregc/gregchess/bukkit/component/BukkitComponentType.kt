package gregc.gregchess.bukkit.component

import gregc.gregchess.bukkit.properties.BukkitGregChessAdapter
import gregc.gregchess.component.ComponentType
import gregc.gregchess.registry.RegisterAll

@RegisterAll(ComponentType::class)
object BukkitComponentType {
    @JvmField
    val ADAPTER = ComponentType<BukkitGregChessAdapter>()
    @JvmField
    val MATCH_CONTROLLER = ComponentType<MatchController>()
    @JvmField
    val SPECTATOR_MANAGER = ComponentType<SpectatorManager>()
}