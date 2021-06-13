package gregc.gregchess.config.dsl

import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asTypeName
import gregc.gregchess.config.*
import kotlin.reflect.KClass

@ConfigDSL
class ConfigObjectScope(private val state: ConfigGeneralState, private val path: String, private val realPath: String) {
    private val fields = mutableListOf<ConfigField>()

    fun formatString(name: String, vararg inputs: KClass<*>, default: String? = null, warnMissing: Boolean = true) {
        fields += ConfigField.ValueFormat(name, inputs.map { it.asTypeName() }, default, warnMissing)
    }

    inline fun <reified T1> formatString1(name: String, default: String? = null, warnMissing: Boolean = true) =
        formatString(name, T1::class, default = default, warnMissing = warnMissing)

    inline fun <reified T1, reified T2> formatString2(
        name: String,
        default: String? = null,
        warnMissing: Boolean = true
    ) =
        formatString(name, T1::class, T2::class, default = default, warnMissing = warnMissing)

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

    operator fun <T> ConfigGeneralState.ConfigType<T>.invoke(name: String, default: T? = null, warnMissing: Boolean = defaulted && (default == null)) {
        fields += ConfigField.Value(name, this, default?.let(toYaml), default?.takeIf { defaulted }?.let(toCode), warnMissing)
    }

    fun <T> ConfigGeneralState.ConfigType<T>.optional(name: String, warnMissing: Boolean = false) {
        fields += ConfigField.ValueOptional(name, this, warnMissing)
    }

    fun <T> ConfigGeneralState.ConfigType<T>.list(name: String, default: List<T>? = null, warnMissing: Boolean = true) {
        fields += ConfigField.ValueList(name, this, default?.map(toYaml), warnMissing)
    }

    fun bySides(white: String, black: String) {
        fields += ConfigField.WhenBlock(sideType, true, null,
            listOf("WHITE" to white.lowerFirst(), "BLACK" to black.lowerFirst()))
    }

    fun byOptSides(white: String, black: String, nulls: String) {
        fields += ConfigField.WhenBlock(sideType, true, nulls.lowerFirst(),
            listOf("WHITE" to white.lowerFirst(), "BLACK" to black.lowerFirst()))
    }

    fun <T : Enum<T>> byEnum(cl: KClass<T>) {
        fields += ConfigField.WhenBlock(cl.asConfigTypeName(), true, null,
            cl.java.enumConstants.map { it.toString() to it.toString().upperSnakeToCamel() })
    }

    fun byOptBool(trues: String, falss: String, nulls: String) {
        fields += ConfigField.WhenBlock(Boolean::class.asTypeName(), false, nulls.lowerFirst(),
            listOf("true" to trues.lowerFirst(), "false" to falss.lowerFirst()))
    }

    inline fun <reified T : Enum<T>> byEnum() = byEnum(T::class)

    fun special(name: String, typ: TypeName, default: String? = null, warnMissing: Boolean? = null) {
        fields += ConfigField.ValueSpecial(name, typ, default, warnMissing)
    }

    fun build() = ConfigField.ObjectBlock(path.split(".").last(), path, realPath, fields)
}
