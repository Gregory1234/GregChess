package gregc.gregchess.bukkit.registry

import gregc.gregchess.AutoRegisterType
import gregc.gregchess.bukkit.BukkitChessModule
import gregc.gregchess.bukkit.GregChess
import gregc.gregchess.bukkit.match.SettingsParser
import gregc.gregchess.bukkit.properties.PropertyType
import gregc.gregchess.bukkit.properties.ScoreboardLayout
import gregc.gregchess.bukkit.renderer.ArenaManager
import gregc.gregchess.bukkit.renderer.ChessFloorRenderer
import gregc.gregchess.bukkit.stats.BukkitPlayerStats
import gregc.gregchess.match.Component
import gregc.gregchess.move.MoveFormatter
import gregc.gregchess.registry.*
import gregc.gregchess.results.EndReason
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.plugin.Plugin
import org.bukkit.scoreboard.Scoreboard
import java.util.*


object BukkitRegistry {
    @JvmField
    val PROPERTY_TYPE = NameRegistry<PropertyType>("property_type")
    @JvmField
    val SETTINGS_PARSER = ConnectedRegistry<_, SettingsParser<out Component>>("settings_parser", Registry.COMPONENT_TYPE)
    @JvmField
    val LOCAL_MOVE_FORMATTER = ConnectedRegistry<_, MoveFormatter>("local_move_formatter", Registry.VARIANT)
    @JvmField
    val FLOOR_RENDERER = ConnectedRegistry<_, ChessFloorRenderer>("floor_renderer", Registry.VARIANT)
    @JvmField
    val QUICK_END_REASONS = ConnectedSetRegistry("quick_end_reasons", Registry.END_REASON)
    @JvmField
    val HOOKED_COMPONENTS = ConnectedSetRegistry("hooked_components", Registry.COMPONENT_TYPE)
    @JvmField
    val BUKKIT_PLUGIN = ConstantRegistry<Plugin>("bukkit_plugin")
    @JvmField
    val CHESS_STATS_PROVIDER = NameRegistry<(UUID) -> BukkitPlayerStats>("chess_stats_provider")
    @JvmField
    val ARENA_MANAGER = NameRegistry<ArenaManager<*>>("arena_manager")
    @JvmField
    val SCOREBOARD_LAYOUT_PROVIDER = NameRegistry<(Scoreboard) -> ScoreboardLayout>("scoreboard_layout_provider")
}

private val BUKKIT_END_REASON_AUTO_REGISTER = AutoRegisterType(EndReason::class) { m, n, e ->
    Registry.END_REASON[m, n] = this
    if ("quick" in e)
        BukkitRegistry.QUICK_END_REASONS += this
}

internal val EndReason.Companion.BUKKIT_AUTO_REGISTER get() = BUKKIT_END_REASON_AUTO_REGISTER



fun String.toKey(): RegistryKey<String> {
    val sections = split(":")
    return when (sections.size) {
        1 -> RegistryKey(GregChess, this)
        2 -> RegistryKey(BukkitChessModule[sections[0]], sections[1])
        else -> throw IllegalArgumentException("Bad registry key: $this")
    }
}

fun <T> ConfigurationSection.getFromRegistry(reg: Registry<String, T, *>, path: String): T? =
    getString(path)?.toKey()?.let { reg[it] }