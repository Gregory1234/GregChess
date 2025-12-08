package gregc.gregchess.bukkit.match

import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkitutils.coroutines.BukkitContext
import gregc.gregchess.bukkitutils.coroutines.BukkitDispatcher
import gregc.gregchess.component.Component
import gregc.gregchess.component.ComponentType
import gregc.gregchess.match.ChessEnvironment
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.time.Clock

@Serializable
object BukkitChessEnvironment : ChessEnvironment {
    override val coroutineDispatcher = BukkitDispatcher(GregChessPlugin.plugin, BukkitContext.SYNC)
    override val clock: Clock get() = Clock.System
    override val zone: TimeZone get() = config.getString("TimeZone")?.let { TimeZone.of(it) } ?: TimeZone.currentSystemDefault()

    override val requiredComponents: Set<ComponentType<*>> get() = BukkitRegistry.REQUIRED_COMPONENTS.elements
    @Transient
    override val impliedComponents: Set<Component> = BukkitRegistry.IMPLIED_COMPONENTS.values.map { it() }.toSet()
}