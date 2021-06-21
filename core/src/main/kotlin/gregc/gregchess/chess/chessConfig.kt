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
    fun pieceName(n: String): LocalizedString
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

interface EndReasonConfig : ConfigBlock {
    companion object {
        operator fun getValue(owner: EndReasonConfig, property: KProperty<*>) =
            owner.getEndReason(property.name.upperFirst())
    }

    fun getEndReason(n: String): LocalizedString
}

val Config.endReason: EndReasonConfig by Config