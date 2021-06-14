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
    val settingsBlocks: Map<String, Map<String, View>>
}

operator fun SettingsConfig.get(name: String): Map<String, View> = settingsBlocks[name].orEmpty()

val SettingsConfig.clock get() = this["Clock"]
val SettingsConfig.presets get() = this["Presets"]

val Config.settings: SettingsConfig by Config

interface ComponentsConfig: ConfigBlock {
    val componentBlocks: Map<String, View>
}

operator fun ComponentsConfig.get(name: String): View = componentBlocks[name]!!
operator fun ComponentsConfig.getValue(owner: ComponentsConfig, property: KProperty<*>) = get(property.name.upperFirst())

val Config.component: ComponentsConfig by Config

val ComponentsConfig.clock by Config.component
val ComponentsConfig.scoreboard by Config.component
val ComponentsConfig.checkCounter by Config.component

interface EndReasonConfig: ConfigBlock {
    fun getEndReason(n: String): String
}

operator fun EndReasonConfig.getValue(owner: EndReasonConfig, property: KProperty<*>) =
    getEndReason(property.name.upperFirst())

val Config.endReason: EndReasonConfig by Config

val EndReasonConfig.checkmate by Config.endReason
val EndReasonConfig.resignation by Config.endReason
val EndReasonConfig.walkover by Config.endReason
val EndReasonConfig.pluginRestart by Config.endReason
val EndReasonConfig.arenaRemoved by Config.endReason
val EndReasonConfig.stalemate by Config.endReason
val EndReasonConfig.insufficientMaterial by Config.endReason
val EndReasonConfig.fiftyMoves by Config.endReason
val EndReasonConfig.repetition by Config.endReason
val EndReasonConfig.drawAgreement by Config.endReason
val EndReasonConfig.timeout by Config.endReason
val EndReasonConfig.drawTimeout by Config.endReason
val EndReasonConfig.error by Config.endReason
val EndReasonConfig.piecesLost by Config.endReason
val EndReasonConfig.atomic by Config.endReason
val EndReasonConfig.kingOfTheHill by Config.endReason
val EndReasonConfig.threeChecks by Config.endReason