package gregc.gregchess.config

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy

class ConfigGeneralState {
    val configTypes = mutableListOf<ConfigType>()
    inner class ConfigType(
        val name: String, val typ: TypeName,
        val defaultCode: String, val parser: String, val defaulted: Boolean = false
    ) {
        override fun toString() = name
        fun toKotlinType(): ClassName = ClassName(PACKAGE_NAME, "Config$name")
        fun toKotlinListType(): ClassName = ClassName(PACKAGE_NAME, "Config${name}List")
        fun toKotlinOptionalType(): ClassName = ClassName(PACKAGE_NAME, "ConfigOptional${name}")

        fun kotlinAppend(b: FileSpec.Builder) {
            b.addType(TypeSpec.classBuilder("Config$name")
                .superclass(configPath.parameterizedBy(typ))
                .primaryConstructor(FunSpec.constructorBuilder()
                    .addParameter("path", String::class)
                    .addParameter(ParameterSpec.builder("default", typ.copy(nullable = true)).defaultValue("null").build())
                    .addParameter(ParameterSpec.builder("warnMissing", Boolean::class).defaultValue("default == null").build())
                    .build())
                .addProperty(PropertySpec.builder("default", typ.copy(nullable = true))
                    .initializer("default")
                    .addModifiers(KModifier.PRIVATE)
                    .build())
                .addProperty(PropertySpec.builder("warnMissing", Boolean::class)
                    .initializer("warnMissing")
                    .addModifiers(KModifier.PRIVATE)
                    .build())
                .addSuperclassConstructorParameter("path")
                .addFunction(FunSpec.builder("get").addModifiers(KModifier.OVERRIDE)
                    .addParameter("c", configurator).returns(typ)
                    .addCode("""return c.get(path, "${name.lowerFirst().camelToSpaces()}", default ?: $defaultCode, warnMissing, $parser)""").build())
                .build())
            b.addType(TypeSpec.classBuilder("ConfigOptional$name")
                .superclass(configPath.parameterizedBy(typ.copy(nullable = true)))
                .primaryConstructor(FunSpec.constructorBuilder()
                    .addParameter("path", String::class)
                    .addParameter(ParameterSpec.builder("warnMissing", Boolean::class).defaultValue("false").build())
                    .build())
                .addProperty(PropertySpec.builder("warnMissing", Boolean::class)
                    .initializer("warnMissing")
                    .addModifiers(KModifier.PRIVATE)
                    .build())
                .addSuperclassConstructorParameter("path")
                .addFunction(FunSpec.builder("get").addModifiers(KModifier.OVERRIDE)
                    .addParameter("c", configurator).returns(typ.copy(nullable = true))
                    .addCode("""return c.get(path, "${name.lowerFirst().camelToSpaces()}", null, warnMissing, $parser)""").build())
                .build())
            b.addType(TypeSpec.classBuilder("Config${name}List")
                .superclass(configPath.parameterizedBy(List::class.asClassName().parameterizedBy(typ)))
                .primaryConstructor(FunSpec.constructorBuilder()
                    .addParameter("path", String::class)
                    .addParameter(ParameterSpec.builder("warnMissing", Boolean::class).defaultValue("true").build())
                    .build())
                .addProperty(PropertySpec.builder("warnMissing", Boolean::class)
                    .initializer("warnMissing")
                    .addModifiers(KModifier.PRIVATE)
                    .build())
                .addSuperclassConstructorParameter("path")
                .addFunction(FunSpec.builder("get").addModifiers(KModifier.OVERRIDE)
                    .addParameter("c", configurator).returns(List::class.asClassName().parameterizedBy(typ))
                    .addCode("""return c.getList(path, "${name.lowerFirst().camelToSpaces()}", warnMissing, $parser)""").build())
                .build())
        }

        init {
            configTypes += this
        }
    }
}



