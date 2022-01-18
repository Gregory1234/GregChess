package gregc.gregchess.bukkit

import gregc.gregchess.ChessModule
import gregc.gregchess.bukkit.chess.*
import gregc.gregchess.bukkit.chess.component.ChessFloorRenderer
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Component
import gregc.gregchess.chess.move.MoveNameFormatter
import gregc.gregchess.chess.variant.ChessVariant
import gregc.gregchess.register
import gregc.gregchess.registry.*
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.plugin.Plugin
import kotlin.reflect.KClass

class SettingsParserContext(val variant: ChessVariant, val section: ConfigurationSection)

typealias SettingsParser<T> = SettingsParserContext.() -> T?

object BukkitRegistry {
    @JvmField
    val PROPERTY_TYPE = NameRegistry<PropertyType>("property_type")
    @JvmField
    val SETTINGS_PARSER = ConnectedRegistry<KClass<out Component>, SettingsParser<out Component>>(
        "settings_parser", Registry.COMPONENT_CLASS
    )
    @JvmField
    val VARIANT_LOCAL_MOVE_NAME_FORMATTER = ConnectedRegistry<ChessVariant, MoveNameFormatter>(
        "variant_local_move_name_formatter", Registry.VARIANT
    )
    @JvmField
    val VARIANT_FLOOR_RENDERER = ConnectedRegistry<ChessVariant, ChessFloorRenderer>(
        "variant_floor_renderer", Registry.VARIANT
    )
    @JvmField
    val QUICK_END_REASONS = ConnectedSetRegistry("quick_end_reasons", Registry.END_REASON)
    @JvmField
    val HOOKED_COMPONENTS = ConnectedSetRegistry("hooked_components", Registry.COMPONENT_CLASS)
    @JvmField
    val BUKKIT_PLUGIN = ConstantRegistry<Plugin>("bukkit_plugin")
}

fun PropertyType.register(module: ChessModule, id: String) = module.register(BukkitRegistry.PROPERTY_TYPE, id, this)

inline fun <reified T : Component> ChessModule.registerSettings(noinline settings: SettingsParser<T>) =
    register(BukkitRegistry.SETTINGS_PARSER, T::class, settings)

fun <T : Component> ChessModule.registerConstSettings(cl: KClass<T>, settings: T) =
    register(BukkitRegistry.SETTINGS_PARSER, cl) { settings }

inline fun <reified T : Component> ChessModule.registerConstSettings(settings: T) =
    registerConstSettings(T::class, settings)

@Suppress("UNCHECKED_CAST")
fun ChessModule.completeSimpleSettings() =
    get(BukkitRegistry.SETTINGS_PARSER)
        .completeWith { cl ->
            if (cl.objectInstance != null) {
                { cl.objectInstance }
            } else {
                { cl.constructors.first { it.parameters.isEmpty() }.call() }
            }
        }

fun ChessModule.registerLocalFormatter(variant: ChessVariant, formatter: MoveNameFormatter) =
    register(BukkitRegistry.VARIANT_LOCAL_MOVE_NAME_FORMATTER, variant, formatter)

fun ChessModule.completeLocalFormatters() =
    get(BukkitRegistry.VARIANT_LOCAL_MOVE_NAME_FORMATTER).completeWith { defaultLocalMoveNameFormatter }

fun ChessModule.registerFloorRenderer(variant: ChessVariant, floorRenderer: ChessFloorRenderer) =
    register(BukkitRegistry.VARIANT_FLOOR_RENDERER, variant, floorRenderer)

fun ChessModule.registerSimpleFloorRenderer(variant: ChessVariant, specialSquares: Collection<Pos>) =
    registerFloorRenderer(variant, simpleFloorRenderer(specialSquares))

fun ChessModule.completeFloorRenderers() =
    get(BukkitRegistry.VARIANT_FLOOR_RENDERER).completeWith { simpleFloorRenderer() }

fun ChessModule.registerHookedComponent(cl: KClass<out Component>) =
    get(BukkitRegistry.HOOKED_COMPONENTS).add(cl)

inline fun <reified T : Component> ChessModule.registerHookedComponent() = registerHookedComponent(T::class)

fun <T : GameScore> EndReason<T>.registerQuick() = apply { module[BukkitRegistry.QUICK_END_REASONS].add(this) }

private val BUKKIT_END_REASON_AUTO_REGISTER = AutoRegisterType(EndReason::class) { m, n, e -> register(m, n); if ("quick" in e) registerQuick() }

internal val EndReason.Companion.BUKKIT_AUTO_REGISTER get() = BUKKIT_END_REASON_AUTO_REGISTER

private val BUKKIT_AUTO_REGISTER_TYPES = listOf(EndReason.BUKKIT_AUTO_REGISTER, PropertyType.AUTO_REGISTER) + AutoRegister.basicTypes

val AutoRegister.Companion.bukkitTypes get() = BUKKIT_AUTO_REGISTER_TYPES

fun ChessModule.registerBukkitPlugin(plugin: Plugin) = get(BukkitRegistry.BUKKIT_PLUGIN).set(plugin)

interface BukkitChessPlugin {
    fun onInitialize()
}
