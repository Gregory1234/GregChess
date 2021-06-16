package gregc.gregchess.chess

import gregc.gregchess.*
import org.bukkit.Material
import org.bukkit.Sound
import kotlin.reflect.KProperty

interface PieceConfig: ConfigBlock {
    fun getName(t: PieceType): String
    fun getChar(t: PieceType): Char
    fun getItem(t: PieceType): BySides<Material>
    fun getStructure(t: PieceType): BySides<List<Material>>
    fun getSound(t: PieceType, s: PieceSound): Sound
}

val Config.piece: PieceConfig by Config

interface SideConfig: ConfigBlock {
    fun getPieceName(s: Side, n: String): String
}

val Config.side: SideConfig by Config

interface ChessConfig: ConfigBlock {
    val capture: String
    fun getFloor(f: Floor): Material
}

val Config.chess: ChessConfig by Config

interface SettingsConfig: ConfigBlock {
    companion object {
        operator fun getValue(owner: SettingsConfig, property: KProperty<*>) = owner[property.name.upperFirst()]
    }
    val settingsBlocks: Map<String, Map<String, View>>
}

operator fun SettingsConfig.get(name: String): Map<String, View> = settingsBlocks[name].orEmpty()

val SettingsConfig.clock by SettingsConfig
val SettingsConfig.presets by SettingsConfig

val Config.settings: SettingsConfig by Config

interface ComponentsConfig: ConfigBlock {
    companion object {
        operator fun getValue(owner: ComponentsConfig, property: KProperty<*>) = owner[property.name.upperFirst()]
    }
    val componentBlocks: Map<String, View>
}

operator fun ComponentsConfig.get(name: String): View = componentBlocks[name]!!

val Config.component: ComponentsConfig by Config

val ComponentsConfig.clock by ComponentsConfig
val ComponentsConfig.scoreboard by ComponentsConfig
val ComponentsConfig.checkCounter by ComponentsConfig

interface EndReasonConfig: ConfigBlock {
    companion object {
        operator fun getValue(owner: EndReasonConfig, property: KProperty<*>) = owner.getEndReason(property.name.upperFirst())
    }
    fun getEndReason(n: String): String
}

val Config.endReason: EndReasonConfig by Config

val EndReasonConfig.checkmate by EndReasonConfig
val EndReasonConfig.resignation by EndReasonConfig
val EndReasonConfig.walkover by EndReasonConfig
val EndReasonConfig.pluginRestart by EndReasonConfig
val EndReasonConfig.arenaRemoved by EndReasonConfig
val EndReasonConfig.stalemate by EndReasonConfig
val EndReasonConfig.insufficientMaterial by EndReasonConfig
val EndReasonConfig.fiftyMoves by EndReasonConfig
val EndReasonConfig.repetition by EndReasonConfig
val EndReasonConfig.drawAgreement by EndReasonConfig
val EndReasonConfig.timeout by EndReasonConfig
val EndReasonConfig.drawTimeout by EndReasonConfig
val EndReasonConfig.error by EndReasonConfig
val EndReasonConfig.piecesLost by EndReasonConfig
val EndReasonConfig.atomic by EndReasonConfig
val EndReasonConfig.kingOfTheHill by EndReasonConfig
val EndReasonConfig.threeChecks by EndReasonConfig