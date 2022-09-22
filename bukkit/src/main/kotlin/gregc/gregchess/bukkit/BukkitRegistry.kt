package gregc.gregchess.bukkit

import gregc.gregchess.CoreRegistry
import gregc.gregchess.bukkit.component.ComponentAlternative
import gregc.gregchess.bukkit.match.*
import gregc.gregchess.bukkit.properties.PropertyType
import gregc.gregchess.bukkit.properties.ScoreboardLayout
import gregc.gregchess.bukkit.renderer.ChessFloorRenderer
import gregc.gregchess.bukkit.stats.BukkitPlayerStats
import gregc.gregchess.component.Component
import gregc.gregchess.move.MoveFormatter
import gregc.gregchess.registry.*
import gregc.gregchess.registry.registry.*
import gregc.gregchess.results.EndReason
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.plugin.Plugin
import org.bukkit.scoreboard.Scoreboard
import java.util.*

// TODO: add a way to register variants with extra functions directly with annotations
object BukkitRegistry {
    @JvmField
    val PROPERTY_TYPE = NameRegistry<PropertyType>("property_type")
    @JvmField
    val SETTINGS_PARSER = ConnectedRegistry<_, SettingsParser<out Component>>("settings_parser", CoreRegistry.COMPONENT_TYPE)
    @JvmField
    val LOCAL_MOVE_FORMATTER = ConnectedRegistry<_, MoveFormatter>("local_move_formatter", CoreRegistry.VARIANT)
    @JvmField
    val FLOOR_RENDERER = ConnectedRegistry<_, ChessFloorRenderer>("floor_renderer", CoreRegistry.VARIANT)
    @JvmField
    val QUICK_END_REASONS = ConnectedSetRegistry("quick_end_reasons", CoreRegistry.END_REASON)
    @JvmField
    val OPTIONAL_COMPONENTS = ConnectedSetRegistry("optional_components", CoreRegistry.COMPONENT_TYPE)
    @JvmField
    val BUKKIT_PLUGIN = ConstantRegistry<Plugin>("bukkit_plugin")
    @JvmField
    val CHESS_STATS_PROVIDER = NameRegistry<(UUID) -> BukkitPlayerStats>("chess_stats_provider")
    @JvmField
    val SCOREBOARD_LAYOUT_PROVIDER = NameRegistry<(Scoreboard) -> ScoreboardLayout>("scoreboard_layout_provider")
    @JvmField
    val VARIANT_OPTIONS_PARSER = ConnectedRegistry<_, VariantOptionsParser>("variant_oprions_parser", CoreRegistry.VARIANT)
    @JvmField
    val COMPONENT_ALTERNATIVE = NameRegistry<ComponentAlternative<*>>("component_alternative")
    @JvmField
    val REQUIRED_COMPONENTS = ConnectedSetRegistry("required_components", CoreRegistry.COMPONENT_TYPE)
    @JvmField
    val REQUIRED_COMPONENT_ALTERNATIVES = ConnectedSetRegistry("hooked_component_alternatives", COMPONENT_ALTERNATIVE)
    @JvmField
    val IMPLIED_COMPONENTS = PartialConnectedRegistry<_, () -> Component>("implied_components", CoreRegistry.COMPONENT_TYPE)
}

private val BUKKIT_END_REASON_AUTO_REGISTER = AutoRegisterType(EndReason::class) { m, n, e ->
    CoreRegistry.END_REASON[m, n] = this
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