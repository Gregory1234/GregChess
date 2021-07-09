package gregc.gregchess.chess

import gregc.gregchess.*
import kotlin.reflect.KProperty

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