package gregc.gregchess.bukkit

import gregc.gregchess.*
import gregc.gregchess.bukkit.chess.*
import gregc.gregchess.bukkit.chess.component.*
import gregc.gregchess.chess.MoveNameTokenType
import gregc.gregchess.chess.component.*
import gregc.gregchess.chess.variant.*
import org.bukkit.NamespacedKey
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.plugin.Plugin
import kotlin.reflect.KClass

class SettingsParserContext(val variant: ChessVariant, val section: ConfigurationSection)

typealias SettingsParser<T> = SettingsParserContext.() -> T?

typealias MoveNameTokenInterpreter<T> = (T) -> String

object BukkitRegistryTypes {
    @JvmField
    val PROPERTY_TYPE = RegistryType<String, PropertyType>("property_type")
    @JvmField
    val SETTINGS_PARSER = RegistryType<KClass<out ComponentData<*>>, SettingsParser<out ComponentData<*>>>("settings_parser", RegistryType.COMPONENT_DATA_CLASS)
    @JvmField
    val MOVE_NAME_TOKEN_STRING = RegistryType<MoveNameTokenType<*>, MoveNameTokenInterpreter<*>>("move_name_token_string", RegistryType.MOVE_NAME_TOKEN_TYPE)
}

fun ChessModule.register(id: String, propertyType: PropertyType) =
    register(BukkitRegistryTypes.PROPERTY_TYPE, id, propertyType)

inline fun <reified T : ComponentData<*>> ChessModule.registerSettings(noinline settings: SettingsParser<T>) =
    register(BukkitRegistryTypes.SETTINGS_PARSER, T::class, settings)

inline fun <reified T : ComponentData<*>> ChessModule.registerConstSettings(settings: T) =
    registerSettings { settings }

fun <T> ChessModule.register(token: MoveNameTokenType<T>, str: MoveNameTokenInterpreter<T> = token.toPgnString) =
    register(BukkitRegistryTypes.MOVE_NAME_TOKEN_STRING, token, str)

abstract class BukkitChessModuleExtension(val plugin: Plugin) : ChessModuleExtension {
    open val config: ConfigurationSection get() = plugin.config

    open val hookedComponents: Set<KClass<out Component>> = emptySet()
}

val ChessModule.bukkit get() = extensions.filterIsInstance<BukkitChessModuleExtension>().first()

interface BukkitChessPlugin {
    fun onInitialize()
}

object BukkitGregChessModule : BukkitChessModuleExtension(GregChess.plugin) {
    private val NO_ARENAS = err("NoArenas")

    private val clockSettings: Map<String, ChessClockData>
        get() = config.getConfigurationSection("Settings.Clock")?.getKeys(false).orEmpty().associateWith {
            val section = gregc.gregchess.bukkit.config.getConfigurationSection("Settings.Clock.$it")!!
            val t = TimeControl.Type.valueOf(section.getString("Type", TimeControl.Type.INCREMENT.toString())!!)
            val initial = section.getString("Initial")?.asDurationOrNull()!!
            val increment = if (t.usesIncrement) section.getString("Increment")?.asDurationOrNull()!! else 0.seconds
            ChessClockData(TimeControl(t, initial, increment))
        }

    override val hookedComponents: Set<KClass<out Component>>
        get() = setOf(Arena.Usage::class, Chessboard::class, ChessClock::class, PlayerManager::class,
            SpectatorManager::class, ScoreboardManager::class, BukkitRenderer::class,
            BukkitEventRelay::class, BukkitGregChessAdapter::class)

    private fun registerSettings() = with(GregChessModule) {
        registerSettings { ArenaManager.freeAreas.firstOrNull().cNotNull(NO_ARENAS) }
        registerSettings { ChessboardState[variant, section.getString("Board")] }
        registerSettings {
            SettingsManager.chooseOrParse(clockSettings, section.getString("Clock")) {
                TimeControl.parseOrNull(it)?.let { t -> ChessClockData(t) }
            }
        }
        registerConstSettings(PlayerManagerData)
        registerConstSettings(SpectatorManagerData)
        registerConstSettings(ScoreboardManagerData)
        registerSettings { BukkitRendererSettings(section.getInt("TileSize", 3)) }
        registerConstSettings(BukkitEventRelayData)
        registerSettings { ThreeChecks.CheckCounterData(section.getInt("CheckLimit", 3).toUInt()) }
        registerConstSettings(AtomicChess.ExplosionManagerData)
        registerConstSettings(BukkitGregChessAdapterData)
    }

    private fun registerMoveNameTokenStrings() = with(GregChessModule) {
        register(MoveNameTokenType.PIECE_TYPE) { it.localChar.uppercase() }
        register(MoveNameTokenType.UNIQUENESS_COORDINATE)
        register(MoveNameTokenType.CAPTURE) { config.getPathString("Chess.Capture") }
        register(MoveNameTokenType.TARGET)
        register(MoveNameTokenType.PROMOTION) { it.localChar.uppercase() }
        register(MoveNameTokenType.CHECK)
        register(MoveNameTokenType.CHECKMATE)
        register(MoveNameTokenType.CASTLE)
        register(MoveNameTokenType.EN_PASSANT) { " e.p." }
    }

    private fun registerComponents() = with(GregChessModule) {
        registerComponent<BukkitEventRelay, BukkitEventRelayData>("bukkit_event_relay")
        registerComponent<BukkitGregChessAdapter, BukkitGregChessAdapterData>("bukkit_adapter")
        registerComponent<BukkitRenderer, BukkitRendererSettings>("bukkit_renderer")
        registerComponent<PlayerManager, PlayerManagerData>("player_manager")
        registerComponent<ScoreboardManager, ScoreboardManagerData>("scoreboard_manager")
        registerComponent<SpectatorManager, SpectatorManagerData>("spectator_manager")
        registerComponent<Arena.Usage, Arena>("arena")
    }

    override fun load() {
        ArenaManager
        ChessGameManager
        ScoreboardManager
        registerComponents()
        registerSettings()
        registerMoveNameTokenStrings()
    }
}

val ChessModule.Companion.pieceTypes get() = modules.flatMap { m ->
    m[RegistryType.PIECE_TYPE].keys.map { NamespacedKey(m.bukkit.plugin, it) }
}
