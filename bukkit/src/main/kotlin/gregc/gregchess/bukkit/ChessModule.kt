package gregc.gregchess.bukkit

import gregc.gregchess.*
import gregc.gregchess.bukkit.chess.*
import gregc.gregchess.bukkit.chess.component.*
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.*
import gregc.gregchess.chess.variant.AtomicChess
import gregc.gregchess.chess.variant.ThreeChecks
import org.bukkit.NamespacedKey
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.plugin.Plugin
import java.time.Duration
import kotlin.reflect.KClass

abstract class BukkitChessModuleExtension(val plugin: Plugin) : ChessModuleExtension {
    open val config: ConfigurationSection get() = plugin.config
    open fun <T> stringify(propertyType: PropertyType<T>, t: T): String = t.toString()
    open fun getSettings(
        requested: Collection<KClass<out Component.Settings<*>>>,
        section: ConfigurationSection
    ): Collection<Component.Settings<*>> = emptyList()

    open fun moveNameTokenToString(type: MoveNameTokenType<*>, value: Any?): String? = null
}

val ChessModule.bukkit get() = extensions.filterIsInstance<BukkitChessModuleExtension>().first()

fun BukkitChessModuleExtension.moveNameTokenToString(token: MoveNameToken<*>) =
    moveNameTokenToString(token.type, token.value)

interface BukkitChessPlugin {
    fun onInitialize()
}

object BukkitGregChessModule : BukkitChessModuleExtension(GregChess.plugin) {
    private val timeFormat: String get() = config.getPathString("TimeFormat")
    private val NO_ARENAS = err("NoArenas")

    override fun <T> stringify(propertyType: PropertyType<T>, t: T): String =
        if (t is Duration)
            t.format(timeFormat) ?: timeFormat
        else
            t.toString()

    override fun moveNameTokenToString(type: MoveNameTokenType<*>, value: Any?): String? =
        if (type == MoveNameTokenType.CAPTURE)
            config.getPathString("Chess.Capture")
        else if ((type == MoveNameTokenType.PROMOTION || type == MoveNameTokenType.PIECE_TYPE) && value is PieceType)
            value.localChar.uppercase()
        else if (type == MoveNameTokenType.EN_PASSANT)
            " e.p."
        else
            null

    private val clockSettings: Map<String, ChessClock.Settings>
        get() = config.getConfigurationSection("Settings.Clock")?.getKeys(false).orEmpty().associateWith {
            val section = gregc.gregchess.bukkit.config.getConfigurationSection("Settings.Clock.$it")!!
            val t = ChessClock.Type.valueOf(section.getString("Type", ChessClock.Type.INCREMENT.toString())!!)
            val initial = section.getString("Initial")?.asDurationOrNull()!!
            val increment = if (t.usesIncrement) section.getString("Increment")?.asDurationOrNull()!! else 0.seconds
            ChessClock.Settings(t, initial, increment)
        }

    override fun getSettings(requested: Collection<KClass<out Component.Settings<*>>>, section: ConfigurationSection) =
        buildList {
            this += ArenaManager.freeAreas.firstOrNull().cNotNull(NO_ARENAS)
            this += Chessboard.Settings[section.getString("Board")]
            SettingsManager.chooseOrParse(clockSettings, section.getString("Clock"), ChessClock.Settings::parse)
                ?.let { this += it }
            this += PlayerManager.Settings
            this += SpectatorManager.Settings
            this += ScoreboardManager.Settings
            this += BukkitRenderer.Settings(section.getInt("TileSize", 3))
            this += BukkitEventRelay.Settings
            if (ThreeChecks.CheckCounter.Settings::class in requested)
                this += ThreeChecks.CheckCounter.Settings(section.getInt("CheckLimit", 3).toUInt())
            if (AtomicChess.ExplosionManager.Settings::class in requested)
                this += AtomicChess.ExplosionManager.Settings
        }

    override fun load() {
        ArenaManager
        ChessGameManager
        ScoreboardManager.Companion
    }
}

val ChessModule.Companion.pieceTypes get() = modules.flatMap { m ->
    m[RegistryType.PIECE_TYPE].keys.map { NamespacedKey(m.bukkit.plugin, it) }
}
