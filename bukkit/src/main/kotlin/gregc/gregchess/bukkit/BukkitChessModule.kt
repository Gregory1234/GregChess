package gregc.gregchess.bukkit

import gregc.gregchess.ChessModule
import gregc.gregchess.bukkit.chess.*
import gregc.gregchess.bukkit.chess.component.ChessFloorRenderer
import gregc.gregchess.chess.EndReason
import gregc.gregchess.chess.MoveFormatter
import gregc.gregchess.chess.component.Component
import gregc.gregchess.chess.component.ComponentType
import gregc.gregchess.chess.variant.ChessVariant
import gregc.gregchess.registry.*
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.plugin.Plugin
import java.util.*

class SettingsParserContext(val variant: ChessVariant, val section: ConfigurationSection, val presetName: String)

typealias SettingsParser<T> = SettingsParserContext.() -> T?

object BukkitRegistry {
    @JvmField
    val PROPERTY_TYPE = NameRegistry<PropertyType>("property_type")
    @JvmField
    val SETTINGS_PARSER = ConnectedRegistry<ComponentType<*>, SettingsParser<out Component>>("settings_parser", Registry.COMPONENT_TYPE)
    @JvmField
    val LOCAL_MOVE_FORMATTER = ConnectedRegistry<ChessVariant, MoveFormatter>("local_move_formatter", Registry.VARIANT)
    @JvmField
    val FLOOR_RENDERER = ConnectedRegistry<ChessVariant, ChessFloorRenderer>("floor_renderer", Registry.VARIANT)
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
}

private val BUKKIT_END_REASON_AUTO_REGISTER = AutoRegisterType(EndReason::class) { m, n, e ->
    Registry.END_REASON[m, n] = this
    if ("quick" in e)
        BukkitRegistry.QUICK_END_REASONS += this
}

internal val EndReason.Companion.BUKKIT_AUTO_REGISTER get() = BUKKIT_END_REASON_AUTO_REGISTER

private val BUKKIT_AUTO_REGISTER_TYPES = listOf(EndReason.BUKKIT_AUTO_REGISTER, PropertyType.AUTO_REGISTER, ArenaManagers.AUTO_REGISTER) + AutoRegister.basicTypes

val AutoRegister.Companion.bukkitTypes get() = BUKKIT_AUTO_REGISTER_TYPES

interface BukkitChessPlugin {
    fun onInitialize()
}

interface BukkitRegistering : Registering {
    override fun registerAll(module: ChessModule) {
        AutoRegister(module, AutoRegister.bukkitTypes).registerAll(this::class)
    }
}

abstract class BukkitChessModule(val plugin: Plugin) : ChessModule(plugin.name, plugin.name.lowercase()) {
    companion object {
        internal val modules = mutableSetOf<ChessModule>()
        operator fun get(namespace: String) = modules.first { it.namespace == namespace }
    }

    final override fun postLoad() {
        BukkitRegistry.BUKKIT_PLUGIN[this] = plugin
        BukkitRegistry.SETTINGS_PARSER[this].completeWith { type -> { type.cl.constructors.first { it.parameters.isEmpty() }.call() } }
        BukkitRegistry.LOCAL_MOVE_FORMATTER[this].completeWith { defaultLocalMoveFormatter }
        BukkitRegistry.FLOOR_RENDERER[this].completeWith { simpleFloorRenderer() }
    }

    final override fun finish() {
        modules += this
    }

    final override fun validate() {
        Registry.REGISTRIES.forEach { it[this].validate() }
    }
}