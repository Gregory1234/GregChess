package gregc.gregchess.bukkit

import gregc.gregchess.*
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

abstract class BukkitChessExtension(module: ChessModule, val plugin: Plugin) : ChessExtension(module, BUKKIT) {
    companion object {
        @JvmField
        internal val BUKKIT = ExtensionType("bukkit")
    }

    open val config: ConfigurationSection get() = plugin.config

    open val hookedComponents: Set<KClass<out Component>> = emptySet()

    open val quickEndReasons: Set<EndReason<*>> = emptySet()

    override fun validate() {
        val components = module[Registry.COMPONENT_CLASS].values
        hookedComponents.forEach {
            requireValid(it in components) { "External component hooked: ${it.componentKey}" }
        }
        val endReasons = module[Registry.END_REASON].values
        quickEndReasons.forEach {
            requireValid(it in endReasons) { "External end reason made quick: ${it.key}" }
        }
    }
}

val ChessModule.bukkit get() = extensions.filterIsInstance<BukkitChessExtension>().first()

interface BukkitChessPlugin {
    fun onInitialize()
}
