package gregc.gregchess.bukkit.match

import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkitutils.coroutines.BukkitContext
import gregc.gregchess.bukkitutils.coroutines.BukkitDispatcher
import gregc.gregchess.component.Component
import gregc.gregchess.component.ComponentType
import gregc.gregchess.match.ChessEnvironment
import gregc.gregchess.match.ChessMatch
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.*
import java.time.Clock
import java.time.ZoneId
import java.util.*

@Serializable
class BukkitChessEnvironment(
    val presetName: String,
    override val pgnRound: Int,
    @Contextual val uuid: UUID = UUID.randomUUID()
) : ChessEnvironment {
    companion object {
        private val bukkitDispatcher = BukkitDispatcher(GregChessPlugin.plugin, BukkitContext.SYNC)
    }
    override val pgnSite: String get() = "GregChess Bukkit plugin"
    override val pgnEventName: String get() = "Casual game" // TODO: add events
    override val coroutineDispatcher: CoroutineDispatcher get() = bukkitDispatcher
    override val clock: Clock get() = config.getString("TimeZone")?.let { Clock.system(ZoneId.of(it)) } ?: Clock.systemDefaultZone()
    override fun matchCoroutineName(): String = uuid.toString()
    override fun matchToString(): String = "uuid=$uuid"

    override val requiredComponents: Set<ComponentType<*>> get() = BukkitRegistry.REQUIRED_COMPONENTS.elements
    @Transient
    override val impliedComponents: Set<Component> = BukkitRegistry.IMPLIED_COMPONENTS.values.map { it() }.toSet()
}

val ChessMatch.bukkitEnvironment get() = environment as BukkitChessEnvironment
val ChessMatch.uuid get() = bukkitEnvironment.uuid
val ChessMatch.presetName get() = bukkitEnvironment.presetName