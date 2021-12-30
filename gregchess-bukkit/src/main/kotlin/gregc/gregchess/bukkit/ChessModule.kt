package gregc.gregchess.bukkit

import gregc.gregchess.ChessModule
import gregc.gregchess.bukkit.chess.*
import gregc.gregchess.bukkit.chess.component.ChessFloorRenderer
import gregc.gregchess.chess.EndReason
import gregc.gregchess.chess.Pos
import gregc.gregchess.chess.component.*
import gregc.gregchess.chess.move.MoveNameFormatter
import gregc.gregchess.chess.variant.ChessVariant
import gregc.gregchess.registry.*
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.plugin.Plugin
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

class SettingsParserContext(val variant: ChessVariant, val section: ConfigurationSection)

typealias SettingsParser<T> = SettingsParserContext.() -> T?

object BukkitRegistry {
    @JvmField
    val PROPERTY_TYPE = NameRegistry<PropertyType>("property_type")
    @JvmField
    val SETTINGS_PARSER = ConnectedRegistry<KClass<out Component>, SettingsParser<out ComponentData<*>>>(
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

fun ChessModule.register(id: String, propertyType: PropertyType) =
    register(BukkitRegistry.PROPERTY_TYPE, id, propertyType)

inline fun <reified T : Component> ChessModule.registerSettings(noinline settings: SettingsParser<ComponentData<T>>) =
    register(BukkitRegistry.SETTINGS_PARSER, T::class, settings)

fun <T : Component> ChessModule.registerConstSettings(cl: KClass<T>, settings: ComponentData<T>) =
    register(BukkitRegistry.SETTINGS_PARSER, cl) { settings }

inline fun <reified T : Component> ChessModule.registerConstSettings(settings: ComponentData<T>) =
    registerConstSettings(T::class, settings)

@Suppress("UNCHECKED_CAST")
fun ChessModule.completeSimpleSettings() =
    get(BukkitRegistry.SETTINGS_PARSER)
        .completeWith { cl ->
            require (cl.isSubclassOf(SimpleComponent::class));
            { SimpleComponentData(cl as KClass<out SimpleComponent>) }
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

fun ChessModule.registerQuickEndReason(endReason: EndReason<*>) =
    get(BukkitRegistry.QUICK_END_REASONS).add(endReason)

fun ChessModule.registerBukkitPlugin(plugin: Plugin) = get(BukkitRegistry.BUKKIT_PLUGIN).set(plugin)

interface BukkitChessPlugin {
    fun onInitialize()
}
