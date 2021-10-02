package gregc.gregchess.bukkit

import gregc.gregchess.*
import gregc.gregchess.bukkit.chess.*
import gregc.gregchess.bukkit.chess.component.*
import gregc.gregchess.chess.MoveNameTokenType
import gregc.gregchess.chess.component.*
import gregc.gregchess.chess.variant.ChessVariant
import gregc.gregchess.chess.variant.ThreeChecks
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.plugin.Plugin
import kotlin.reflect.KClass

class SettingsParserContext(val variant: ChessVariant, val section: ConfigurationSection)

typealias SettingsParser<T> = SettingsParserContext.() -> T?

typealias MoveNameTokenInterpreter<T> = (T) -> String

object BukkitRegistryTypes {
    @JvmField
    val PROPERTY_TYPE = NameRegistryType<PropertyType>("property_type")
    @JvmField
    val SETTINGS_PARSER =
        SingleConnectedRegistryType<KClass<out ComponentData<*>>, SettingsParser<out ComponentData<*>>>(
            "settings_parser", RegistryType.COMPONENT_DATA_CLASS
        )
    @JvmField
    val MOVE_NAME_TOKEN_STRING = SingleConnectedRegistryType<MoveNameTokenType<*>, MoveNameTokenInterpreter<*>>(
        "move_name_token_string", RegistryType.MOVE_NAME_TOKEN_TYPE
    )
}

fun ChessModule.register(id: String, propertyType: PropertyType) =
    register(BukkitRegistryTypes.PROPERTY_TYPE, id, propertyType)

inline fun <reified T : ComponentData<*>> ChessModule.registerSettings(noinline settings: SettingsParser<T>) =
    register(BukkitRegistryTypes.SETTINGS_PARSER, T::class, settings)

fun <T : ComponentData<*>> ChessModule.registerConstSettings(cl: KClass<T>, settings: T) =
    register(BukkitRegistryTypes.SETTINGS_PARSER, cl) { settings }

inline fun <reified T : ComponentData<*>> ChessModule.registerConstSettings(settings: T) =
    registerConstSettings(T::class, settings)

fun <T : Any> ChessModule.register(token: MoveNameTokenType<T>, str: MoveNameTokenInterpreter<T> = token.toPgnString) =
    register(BukkitRegistryTypes.MOVE_NAME_TOKEN_STRING, token, str)

abstract class BukkitChessExtension(module: ChessModule, val plugin: Plugin) : ChessExtension(module, BUKKIT) {
    companion object {
        @JvmField
        internal val BUKKIT = ExtensionType("bukkit")
    }

    open val config: ConfigurationSection get() = plugin.config

    open val hookedComponents: Set<KClass<out Component>> = emptySet()

    override fun validate() {
        val components = module[RegistryType.COMPONENT_CLASS].values
        hookedComponents.forEach {
            requireValid(it in components) { "External component hooked: ${it.componentKey}" }
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
            val section = gregc.gregchess.bukkit.config.getConfigurationSection("Settings.Clock.$it")!!
            val t = TimeControl.Type.valueOf(section.getString("Type", TimeControl.Type.INCREMENT.toString())!!)
            val initial = section.getString("Initial")?.asDurationOrNull()!!
            val increment = if (t.usesIncrement) section.getString("Increment")?.asDurationOrNull()!! else 0.seconds
            ChessClockData(TimeControl(t, initial, increment))
        }

    override val hookedComponents: Set<KClass<out Component>>
        get() = setOf(
            Chessboard::class, ChessClock::class, PlayerManager::class,
            SpectatorManager::class, ScoreboardManager::class, BukkitRenderer::class,
            BukkitEventRelay::class, BukkitGregChessAdapter::class
        )

    private fun registerSettings() = with(GregChessModule) {
        registerSettings { ChessboardState[variant, section.getString("Board")] }
        registerSettings {
            // TODO: report failure
            SettingsManager.chooseOrParse(clockSettings, section.getString("Clock")) {
                TimeControl.parseOrNull(it)?.let { t -> ChessClockData(t) }
            }
        }
        registerConstSettings(PlayerManagerData)
        registerConstSettings(SpectatorManagerData)
        registerConstSettings(ScoreboardManagerData)
        registerSettings { BukkitRendererSettings() }
        registerConstSettings(BukkitEventRelayData)
        registerSettings { ThreeChecks.CheckCounterData(section.getInt("CheckLimit", 3).toUInt()) }
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
    }

    private fun registerPlayerTypes() = with(GregChessModule) {
        registerPlayerType<BukkitPlayerInfo>("bukkit")
        registerPlayerType<Stockfish>("stockfish")
    }

    override fun load() {
        Arena
        ChessGameManager
        ScoreboardManager
        registerComponents()
        registerSettings()
        registerMoveNameTokenStrings()
        registerPlayerTypes()
    }
}