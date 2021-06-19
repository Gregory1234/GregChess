package gregc.gregchess.chess

import gregc.gregchess.*
import kotlin.reflect.KProperty

interface PieceTypeConfig {
    val type: PieceType
    val name: LocalizedString
    val char: Localized<Char>
}

interface SideConfig {
    val side: Side
    fun pieceName(n: String): String
}

interface ChessConfig : ConfigBlock {
    val capture: LocalizedString
    fun getPieceType(p: PieceType): PieceTypeConfig
    fun getSide(s: Side): SideConfig
}

val Config.chess: ChessConfig by Config

interface SettingsConfig : ConfigBlock {
    companion object {
        operator fun getValue(owner: SettingsConfig, property: KProperty<*>) =
            owner.getSettings(property.name.upperFirst())
    }

    val settingsBlocks: Map<String, Map<String, View>>
    fun getSettings(n: String): Map<String, View>
}

val SettingsConfig.clock by SettingsConfig
val SettingsConfig.presets by SettingsConfig

val Config.settings: SettingsConfig by Config

interface ComponentsConfig : ConfigBlock {
    companion object {
        operator fun getValue(owner: ComponentsConfig, property: KProperty<*>) =
            owner.getComponent(property.name.upperFirst())
    }

    val componentBlocks: Map<String, View>
    fun getComponent(n: String): View
}

val Config.component: ComponentsConfig by Config

val ComponentsConfig.clock by ComponentsConfig
val ComponentsConfig.scoreboard by ComponentsConfig
val ComponentsConfig.checkCounter by ComponentsConfig

interface EndReasonConfig : ConfigBlock {
    companion object {
        operator fun getValue(owner: EndReasonConfig, property: KProperty<*>) =
            owner.getEndReason(property.name.upperFirst())
    }

    fun getEndReason(n: String): String
}

val Config.endReason: EndReasonConfig by Config

val EndReasonConfig.checkmate by EndReasonConfig
val EndReasonConfig.resignation by EndReasonConfig
val EndReasonConfig.walkover by EndReasonConfig
val EndReasonConfig.stalemate by EndReasonConfig
val EndReasonConfig.insufficientMaterial by EndReasonConfig
val EndReasonConfig.fiftyMoves by EndReasonConfig
val EndReasonConfig.repetition by EndReasonConfig
val EndReasonConfig.drawAgreement by EndReasonConfig
val EndReasonConfig.timeout by EndReasonConfig
val EndReasonConfig.drawTimeout by EndReasonConfig
val EndReasonConfig.piecesLost by EndReasonConfig
val EndReasonConfig.error by EndReasonConfig
val EndReasonConfig.atomic by EndReasonConfig
val EndReasonConfig.kingOfTheHill by EndReasonConfig
val EndReasonConfig.threeChecks by EndReasonConfig