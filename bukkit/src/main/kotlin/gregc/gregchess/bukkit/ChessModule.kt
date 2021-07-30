package gregc.gregchess.bukkit

import gregc.gregchess.*
import gregc.gregchess.bukkit.chess.ArenaManager
import gregc.gregchess.bukkit.chess.ChessGameManager
import gregc.gregchess.bukkit.chess.component.ScoreboardManager
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.PropertyType
import gregc.gregchess.chess.variant.ChessVariant
import org.bukkit.configuration.ConfigurationSection
import java.time.Duration

interface BukkitChessModuleExtension: ChessModuleExtension {
    val config: ConfigurationSection
    fun <T> stringify(propertyType: PropertyType<T>, t: T): String
}

val ChessModule.bukkit
    get() = if (this is BukkitChessModuleExtension) this
    else extensions.filterIsInstance<BukkitChessModuleExtension>().first()

private val timeFormat: String get() = config.getString("TimeFormat")!!

object BukkitGregChessModule: BukkitChessModuleExtension {
    private val endReasons_ = mutableListOf<EndReason<*>>()
    private val propertyTypes_ = mutableListOf<PropertyType<*>>()
    internal fun <T: GameScore> register(endReason: EndReason<T>): EndReason<T> { endReasons_ += endReason; return endReason }
    internal fun <T> register(propertyType: PropertyType<T>): PropertyType<T> { propertyTypes_ += propertyType; return propertyType }
    override val pieceTypes = emptyList<PieceType>()
    override val variants = emptyList<ChessVariant>()
    override val endReasons get() = endReasons_.toList()
    override val propertyTypes get() = propertyTypes_.toList()
    override val extensions: MutableList<ChessModuleExtension> = mutableListOf()
    override val base: ChessModule = GregChessModule
    override val config: ConfigurationSection get() = GregChess.plugin.config
    override fun <T> stringify(propertyType: PropertyType<T>, t: T): String =
        if (t is Duration)
            t.format(timeFormat) ?: timeFormat
        else
            t.toString()

    override fun load() {
        ArenaManager
        ChessGameManager
        ScoreboardManager.Companion
    }
}
