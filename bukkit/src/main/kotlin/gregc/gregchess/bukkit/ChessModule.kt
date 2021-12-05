package gregc.gregchess.bukkit

import gregc.gregchess.*
import gregc.gregchess.bukkit.chess.*
import gregc.gregchess.bukkit.chess.component.*
import gregc.gregchess.bukkit.chess.player.BukkitPlayer
import gregc.gregchess.bukkit.chess.player.Stockfish
import gregc.gregchess.chess.EndReason
import gregc.gregchess.chess.FEN
import gregc.gregchess.chess.component.*
import gregc.gregchess.chess.move.MoveNameFormatter
import gregc.gregchess.chess.player.ChessPlayerType
import gregc.gregchess.chess.player.enginePlayerType
import gregc.gregchess.chess.variant.*
import gregc.gregchess.registry.*
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.plugin.Plugin
import kotlin.reflect.KClass
import kotlin.time.Duration

class SettingsParserContext(val variant: ChessVariant, val section: ConfigurationSection)

typealias SettingsParser<T> = SettingsParserContext.() -> T?

object BukkitRegistryTypes {
    @JvmField
    val PROPERTY_TYPE = NameRegistryType<PropertyType>("property_type")
    @JvmField
    val SETTINGS_PARSER =
        SingleConnectedRegistryType<KClass<out Component>, SettingsParser<out ComponentData<*>>>(
            "settings_parser", RegistryType.COMPONENT_CLASS
        )
    @JvmField
    val VARIANT_LOCAL_MOVE_NAME_FORMATTER = SingleConnectedRegistryType<ChessVariant, MoveNameFormatter>(
        "variant_local_move_name_formatter", RegistryType.VARIANT
    )
}

fun ChessModule.register(id: String, propertyType: PropertyType) =
    register(BukkitRegistryTypes.PROPERTY_TYPE, id, propertyType)

inline fun <reified T : Component> ChessModule.registerSettings(noinline settings: SettingsParser<ComponentData<T>>) =
    register(BukkitRegistryTypes.SETTINGS_PARSER, T::class, settings)

fun <T : Component> ChessModule.registerConstSettings(cl: KClass<T>, settings: ComponentData<T>) =
    register(BukkitRegistryTypes.SETTINGS_PARSER, cl) { settings }

inline fun <reified T : Component> ChessModule.registerConstSettings(settings: ComponentData<T>) =
    registerConstSettings(T::class, settings)

inline fun <reified T : SimpleComponent> ChessModule.registerSimpleSettings() =
    registerConstSettings(T::class, SimpleComponentData(T::class))

fun ChessModule.registerLocalFormatter(
    variant: ChessVariant,
    formatter: MoveNameFormatter = MoveNameFormatter { defaultFormatMoveNameLocal(it) }
) = register(BukkitRegistryTypes.VARIANT_LOCAL_MOVE_NAME_FORMATTER, variant, formatter)

abstract class BukkitChessExtension(module: ChessModule, val plugin: Plugin) : ChessExtension(module, BUKKIT) {
    companion object {
        @JvmField
        internal val BUKKIT = ExtensionType("bukkit")
    }

    open val config: ConfigurationSection get() = plugin.config

    open val hookedComponents: Set<KClass<out Component>> = emptySet()

    open val quickEndReasons: Set<EndReason<*>> = emptySet()

    override fun validate() {
        val components = module[RegistryType.COMPONENT_CLASS].values
        hookedComponents.forEach {
            requireValid(it in components) { "External component hooked: ${it.componentKey}" }
        }
        val endReasons = module[RegistryType.END_REASON].values
        quickEndReasons.forEach {
            requireValid(it in endReasons) { "External end reason made quick: ${it.key}" }
        }
    }
}

val ChessModule.bukkit get() = extensions.filterIsInstance<BukkitChessExtension>().first()

interface BukkitChessPlugin {
    fun onInitialize()
}

object BukkitGregChessModule : BukkitChessExtension(GregChessModule, GregChess.plugin) {

    private val clockSettings: Map<String, ChessClockData>
        get() = config.getConfigurationSection("Settings.Clock")?.getKeys(false).orEmpty().associateWith {
            val section = config.getConfigurationSection("Settings.Clock.$it")!!
            val t = TimeControl.Type.valueOf(section.getString("Type", TimeControl.Type.INCREMENT.toString())!!)
            val initial = section.getString("Initial")!!.toDuration()
            val increment = if (t.usesIncrement) section.getString("Increment")!!.toDuration() else Duration.ZERO
            ChessClockData(TimeControl(t, initial, increment))
        }

    override val hookedComponents: Set<KClass<out Component>>
        get() = setOf(
            Chessboard::class, ChessClock::class, GameController::class,
            SpectatorManager::class, ScoreboardManager::class, BukkitRenderer::class,
            BukkitEventRelay::class, BukkitGregChessAdapter::class
        )

    override val quickEndReasons: Set<EndReason<*>>
        get() = setOf(Arena.ARENA_REMOVED, ChessGameManager.PLUGIN_RESTART)

    private fun registerSettings() = with(GregChessModule) {
        registerSettings {
            when(val name = section.getString("Board")) {
                null -> ChessboardState(variant)
                "normal" -> ChessboardState(variant)
                "chess960" -> ChessboardState(variant, chess960 = true)
                else -> if (name.startsWith("fen ")) try {
                    ChessboardState(variant, FEN.parseFromString(name.drop(4)))
                } catch (e: FEN.FENFormatException) {
                    GregChessModule.logger.warn("Chessboard configuration ${e.fen} is in a wrong format, defaulted to normal: ${e.cause?.message}!")
                    ChessboardState(variant)
                } else {
                    GregChessModule.logger.warn("Invalid chessboard configuration $name, defaulted to normal!")
                    ChessboardState(variant)
                }
            }
        }
        registerSettings {
            SettingsManager.chooseOrParse(clockSettings, section.getString("Clock")) {
                TimeControl.parseOrNull(it)?.let { t -> ChessClockData(t) } ?: run {
                    logger.warn("Bad time control \"$it\", defaulted to none")
                    null
                }
            }
        }
        registerSimpleSettings<GameController>()
        registerSimpleSettings<SpectatorManager>()
        registerSimpleSettings<ScoreboardManager>()
        registerSettings { BukkitRendererSettings() }
        registerSimpleSettings<BukkitEventRelay>()
        registerSettings { ThreeChecks.CheckCounterData(section.getInt("CheckLimit", 3).toUInt()) }
        registerSimpleSettings<BukkitGregChessAdapter>()
    }

    private fun registerLocalFormatters() = with(GregChessModule) {
        // TODO: add default values in registries
        registerLocalFormatter(ChessVariant.Normal)
        registerLocalFormatter(Antichess)
        registerLocalFormatter(AtomicChess)
        registerLocalFormatter(CaptureAll)
        registerLocalFormatter(HordeChess)
        registerLocalFormatter(KingOfTheHill)
        registerLocalFormatter(ThreeChecks)
    }

    private fun registerComponents() = with(GregChessModule) {
        registerSimpleComponent<BukkitEventRelay>("bukkit_event_relay")
        registerSimpleComponent<BukkitGregChessAdapter>("bukkit_adapter")
        registerComponent<BukkitRenderer, BukkitRendererSettings>("bukkit_renderer")
        registerSimpleComponent<GameController>("game_controller")
        registerSimpleComponent<ScoreboardManager>("scoreboard_manager")
        registerSimpleComponent<SpectatorManager>("spectator_manager")
    }

    private fun registerPlayerTypes() = with(GregChessModule) {
        register("bukkit", ChessPlayerType(PlayerSerializer) { c, g -> BukkitPlayer(this, c, g) })
        register("stockfish", enginePlayerType<Stockfish>())
    }

    override fun load() {
        Arena
        ChessGameManager
        ScoreboardManager
        registerComponents()
        registerSettings()
        registerLocalFormatters()
        registerPlayerTypes()
    }
}