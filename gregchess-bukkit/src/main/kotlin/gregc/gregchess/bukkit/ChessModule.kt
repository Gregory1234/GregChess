package gregc.gregchess.bukkit

import gregc.gregchess.ChessModule
import gregc.gregchess.bukkit.chess.*
import gregc.gregchess.bukkit.chess.component.ChessFloorRenderer
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Component
import gregc.gregchess.chess.component.ComponentType
import gregc.gregchess.chess.move.MoveNameFormatter
import gregc.gregchess.chess.variant.ChessVariant
import gregc.gregchess.register
import gregc.gregchess.registry.*
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.plugin.Plugin

class SettingsParserContext(val variant: ChessVariant, val section: ConfigurationSection)

typealias SettingsParser<T> = SettingsParserContext.() -> T?

object BukkitRegistry {
    @JvmField
    val PROPERTY_TYPE = NameRegistry<PropertyType>("property_type")
    @JvmField
    val SETTINGS_PARSER = DefaultedConnectedRegistry<String, ComponentType<*>, SettingsParser<out Component>>(
        "settings_parser", Registry.COMPONENT_TYPE
    ) { type -> { type.key.cl.constructors.first { it.parameters.isEmpty() }.call() } }
    @JvmField
    val VARIANT_LOCAL_MOVE_NAME_FORMATTER = DefaultedConnectedRegistry(
        "variant_local_move_name_formatter", Registry.VARIANT
    ) { defaultLocalMoveNameFormatter }
    @JvmField
    val VARIANT_FLOOR_RENDERER = DefaultedConnectedRegistry(
        "variant_floor_renderer", Registry.VARIANT
    ) { simpleFloorRenderer() }
    @JvmField
    val QUICK_END_REASONS = ConnectedSetRegistry("quick_end_reasons", Registry.END_REASON)
    @JvmField
    val HOOKED_COMPONENTS = ConnectedSetRegistry("hooked_components", Registry.COMPONENT_TYPE)
    @JvmField
    val BUKKIT_PLUGIN = ConstantRegistry<Plugin>("bukkit_plugin")
}

fun PropertyType.register(module: ChessModule, id: String) = module.register(BukkitRegistry.PROPERTY_TYPE, id, this)

fun <T : Component> ComponentType<T>.registerSettings(settings: SettingsParser<T>) =
    apply { module.register(BukkitRegistry.SETTINGS_PARSER, this, settings) }

fun <T : Component> ComponentType<T>.registerConstSettings(settings: T) =
    apply { module.register(BukkitRegistry.SETTINGS_PARSER, this) { settings } }

fun ChessVariant.registerLocalFormatter(formatter: MoveNameFormatter) =
    module.register(BukkitRegistry.VARIANT_LOCAL_MOVE_NAME_FORMATTER, this, formatter)

fun ChessVariant.registerFloorRenderer(floorRenderer: ChessFloorRenderer) =
    module.register(BukkitRegistry.VARIANT_FLOOR_RENDERER, this, floorRenderer)

fun ChessVariant.registerSimpleFloorRenderer(specialSquares: Collection<Pos>) =
    registerFloorRenderer(simpleFloorRenderer(specialSquares))

fun <T : Component> ComponentType<T>.registerHooked() =
    apply { module[BukkitRegistry.HOOKED_COMPONENTS].add(this) }

fun <T : GameScore> EndReason<T>.registerQuick() = apply { module[BukkitRegistry.QUICK_END_REASONS].add(this) }

private val BUKKIT_END_REASON_AUTO_REGISTER = AutoRegisterType(EndReason::class) { m, n, e -> register(m, n); if ("quick" in e) registerQuick() }

internal val EndReason.Companion.BUKKIT_AUTO_REGISTER get() = BUKKIT_END_REASON_AUTO_REGISTER

private val BUKKIT_AUTO_REGISTER_TYPES = listOf(EndReason.BUKKIT_AUTO_REGISTER, PropertyType.AUTO_REGISTER) + AutoRegister.basicTypes

val AutoRegister.Companion.bukkitTypes get() = BUKKIT_AUTO_REGISTER_TYPES

fun ChessModule.registerBukkitPlugin(plugin: Plugin) = get(BukkitRegistry.BUKKIT_PLUGIN).set(plugin)

interface BukkitChessPlugin {
    fun onInitialize()
}

interface BukkitRegistering : Registering {
    override fun registerAll(module: ChessModule) {
        AutoRegister(module, AutoRegister.bukkitTypes).registerAll(this::class)
    }
}