package gregc.gregchess.config.dsl

import com.squareup.kotlinpoet.*
import gregc.gregchess.config.ConfigGeneralState
import kotlin.reflect.KClass

@ConfigDSL
class ConfigTypeBuilder<T>(val name: String, val defaulted: Boolean) {
    var baseType: TypeName = String::class.asClassName()
    var baseClass: KClass<*>
        get() = throw UnsupportedOperationException()
        set(v) {
            baseType = v.asClassName()
        }
    var defaultCode: CodeBlock = CodeBlock.of("")
    var default: T
        get() = throw UnsupportedOperationException()
        set(v) {
            defaultCode = toCode(v)
        }
    var parser: CodeBlock = CodeBlock.of("{ null }")

    lateinit var toCode: (T) -> CodeBlock

    lateinit var toYaml: (T) -> String

    fun build(s: ConfigGeneralState): ConfigGeneralState.ConfigType<T> {
        return s.ConfigType(name, baseType, defaultCode, parser, defaulted, toCode, toYaml)
    }

}