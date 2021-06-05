package gregc.gregchess.config.dsl

import gregc.gregchess.config.ConfigField
import gregc.gregchess.config.addDot

class ConfigFiniteBlockList(val pattern: ConfigClassScope, val path: String, val realPath: String) {
    val instances = mutableListOf<ConfigField.ObjectBlock>()
    fun addInstance(name: String, block: ConfigObjectScope.() -> Unit) {
        instances += ConfigObjectScope(pattern.state, path addDot name, realPath addDot name).apply(block).build()
    }
    fun build() = ConfigField.FiniteBlockList(pattern.build(), instances)
}