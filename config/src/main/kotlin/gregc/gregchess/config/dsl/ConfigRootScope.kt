package gregc.gregchess.config.dsl

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import gregc.gregchess.config.*

@ConfigDSL
class ConfigRootScope(private val state: ConfigGeneralState, private val name: String) {
    constructor(base: String): this(ConfigGeneralState(), base)
    private val rootBlocks = mutableListOf<ConfigField.ObjectBlock>()

    fun root(name: String, realPath: String = "", block: ConfigObjectScope.() -> Unit) {
        rootBlocks += ConfigObjectScope(state, name, realPath).apply(block).build()
    }

    fun buildKotlin(): FileSpec = FileSpec.builder(PACKAGE_NAME, name)
        .apply {
            (1u..2u).forEach {
                formatString(this, it)
            }
            state.configTypes.forEach {
                it.kotlinAppend(this)
            }
            rootBlocks.forEach {
                addType(it.toKotlinObject())
            }
        }.build()

    fun <T> generalType(name: String, defaulted: Boolean, block: ConfigTypeBuilder<T>.() -> Unit) =
        ConfigTypeBuilder<T>(name, defaulted).apply(block).build(state)

    inline fun <reified T> type(name: String, crossinline block: ConfigTypeBuilder<T>.() -> Unit) =
        generalType<T>(name, false) {
            baseClass = T::class
            block()
        }

    inline fun <reified T> defaultType(name: String, crossinline block: ConfigTypeBuilder<T>.() -> Unit) =
        generalType<T>(name, true) {
            baseClass = T::class
            block()
        }

    fun formatString(b: FileSpec.Builder, i: UInt) {
        val params = (1u..i).map { ParameterSpec("a$it", TypeVariableName("T$it")) }
        val flam = LambdaTypeName.get(
            receiver = configurator,
            parameters = params,
            returnType = TypeVariableName("R"))
        val args = (1u..i).joinToString { "a$it" }
        b.addType(TypeSpec.classBuilder("ConfigFunction$i")
            .apply { (1u..i).forEach { addTypeVariable(TypeVariableName("T$it")) } }
            .addTypeVariable(TypeVariableName("R"))
            .primaryConstructor(FunSpec.constructorBuilder()
                .addParameter("c", configurator).addParameter("block", flam).build())
            .addProperty(PropertySpec.builder("c", configurator, KModifier.PRIVATE).initializer("c").build())
            .addProperty(PropertySpec.builder("block", flam, KModifier.PRIVATE).initializer("block").build())
            .apply {
                binaryStrings(i).forEach { bin ->
                    addFunction(FunSpec.builder("invoke").addModifiers(KModifier.OPERATOR)
                        .addParameters(params.zip(bin) { v, b ->
                            if (b) ParameterSpec(v.name, configVal.parameterizedBy(v.type)) else v
                        }).returns(TypeVariableName("R"))
                        .addCode("return c.block(" + ((1u..i).zip(bin).joinToString { (i, b) -> if (b) "a$i.get(c)" else "a$i" }) + ")").build())
                }
            }.build())
        val funi = ClassName(PACKAGE_NAME, "ConfigFunction$i")
            .parameterizedBy((1u..i).map {TypeVariableName("T$it")} + String::class.asClassName())
        b.addType(TypeSpec.classBuilder("ConfigFormatString$i")
            .apply { (1u..i).forEach { addTypeVariable(TypeVariableName("T$it")) } }
            .superclass(configPath.parameterizedBy(funi))
            .primaryConstructor(FunSpec.constructorBuilder().addParameter("path", String::class).build())
            .addSuperclassConstructorParameter("path")
            .addFunction(
                FunSpec.builder("get").addModifiers(KModifier.OVERRIDE)
                .addParameter("c", configurator).returns(funi)
                .addCode("""return %T(c){ $args -> getFormatString(path, $args) }""", funi).build())
            .apply {
                binaryStrings(i).forEach { bin ->
                    addFunction(FunSpec.builder("invoke").addModifiers(KModifier.OPERATOR)
                        .addParameters(params.zip(bin) { v, b ->
                            if (b) ParameterSpec(v.name, configVal.parameterizedBy(v.type)) else v
                        }).returns(configFullFormatString)
                        .addCode("return %T(path, $args)", configFullFormatString).build()
                    )
                }
            }.build())
    }

    fun buildYaml(): YamlBlock = YamlBlock(mutableMapOf()).apply {
        rootBlocks.forEach {
            if (it.realPath == "")
                it.yamlAppend(this)
            else {
                var n = this
                it.realPath.split(".").forEach { s ->
                    n.with(s) {
                        n = this
                    }
                }
                it.yamlAppend(n)
            }
        }
    }
}