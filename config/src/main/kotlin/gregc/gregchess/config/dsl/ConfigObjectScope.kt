package gregc.gregchess.config.dsl

import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asTypeName
import gregc.gregchess.config.*
import kotlin.reflect.KClass

@ConfigDSL
class ConfigObjectScope(private val state: ConfigGeneralState, private val path: String, private val realPath: String) {
    private val fields = mutableListOf<ConfigField>()

    fun formatString(name: String, vararg inputs: KClass<*>, default: String? = null, warnMissing: Boolean = true) {
        fields += ConfigField.ValueFormat(name, inputs.map {it.asTypeName()}, default, warnMissing)
    }

    fun block(name: String, block: ConfigObjectScope.() -> Unit) {
        fields += ConfigObjectScope(state, path addDot name, realPath addDot name).apply(block).build()
    }

    fun inlineFiniteBlockList(name: String, block: ConfigClassScope.() -> Unit): ConfigFiniteBlockList =
        ConfigFiniteBlockList(ConfigClassScope(state, name).apply(block), path, realPath).apply {
            fields += build()
        }

    fun blockList(name: String, block: ConfigClassScope.() -> Unit) {
        fields += ConfigField.BlockList(name, ConfigClassScope(state, name + "Element").apply(block).build())
    }

    operator fun ConfigGeneralState.ConfigType.invoke(name: String, default: String? = null, warnMissing: Boolean = defaulted && (default == null)) {
        fields += ConfigField.Value(name, this, default, if (defaulted) default else null, warnMissing)
    }

    operator fun ConfigGeneralState.ConfigType.invoke(name: String, defaultc: Char, warnMissing: Boolean = false) {
        val default = "'$defaultc'"
        fields += ConfigField.Value(name, this, default, if (defaulted) default else null, warnMissing)
    }

    fun ConfigGeneralState.ConfigType.optional(name: String, warnMissing: Boolean = false) {
        fields += ConfigField.ValueOptional(name, this, warnMissing)
    }

    fun ConfigGeneralState.ConfigType.list(name: String, default: List<String>? = null, warnMissing: Boolean = true) {
        fields += ConfigField.ValueList(name, this, default, warnMissing)
    }

    fun enumString(name: String, typ: TypeName, default: String, warnMissing: Boolean = true) {
        fields += ConfigField.EnumString(name, typ, default, warnMissing)
    }

    fun enumStringList(name: String, typ: TypeName, default: List<String>? = null, warnMissing: Boolean = true) {
        fields += ConfigField.EnumStringList(name, typ, default, warnMissing)
    }

    fun bySides(white: String, black: String) {
        fields += ConfigField.WhenBlock(sideType, "WHITE" to white.lowerFirst(), "BLACK" to black.lowerFirst())
    }

    fun byOptSides(white: String, black: String, nulls: String) {
        fields += ConfigField.WhenBlock(sideType, nulls.lowerFirst(), "WHITE" to white.lowerFirst(), "BLACK" to black.lowerFirst())
    }

    fun byEnum(typ: TypeName, vararg vals: String) {
        fields += ConfigField.WhenBlock(typ, null, vals.map {it.camelToUpperSnake() to it.lowerFirst()})
    }

    fun build() = ConfigField.ObjectBlock(path.split(".").last(), path, realPath, fields)
}
