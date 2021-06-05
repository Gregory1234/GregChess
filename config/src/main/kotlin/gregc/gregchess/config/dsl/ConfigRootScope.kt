package gregc.gregchess.config.dsl

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import gregc.gregchess.config.*

@ConfigDSL
class ConfigRootScope(private val state: ConfigGeneralState, private val name: String) {
    constructor(base: String): this(ConfigGeneralState(), base)
    private val rootBlock = ConfigObjectScope(state, name, "")

    fun root(block: ConfigObjectScope.() -> Unit) = rootBlock.run(block)

    fun buildKotlin(): FileSpec = FileSpec.builder(PACKAGE_NAME, name)
        .apply {
            (1..2).forEach {
                formatString(this, it)
            }
            state.configTypes.forEach {
                it.kotlinAppend(this)
            }
            addType(rootBlock.build().toKotlinObject())
        }.build()

    fun type(name: String, typ: TypeName,
             defaultCode: String, parser: String, defaulted: Boolean = false) = with(state) {
        ConfigType(name, typ, defaultCode, parser, defaulted)
    }

    fun formatString(b: FileSpec.Builder, i: Int) {
        val params = (1..i).map { ParameterSpec("a$it", TypeVariableName("T$it")) }
        val lam = LambdaTypeName.get(
            parameters = params,
            returnType = String::class.asTypeName())
        val args = (1..i).joinToString { "a$it" }
        b.addType(TypeSpec.classBuilder("ConfigFormatString$i")
            .apply { (1..i).forEach { addTypeVariable(TypeVariableName("T$it")) } }
            .superclass(configPath.parameterizedBy(lam))
            .primaryConstructor(FunSpec.constructorBuilder().addParameter("path", String::class).build())
            .addSuperclassConstructorParameter("path")
            .addFunction(
                FunSpec.builder("get").addModifiers(KModifier.OVERRIDE)
                .addParameter("c", configurator).returns(lam)
                .addCode("""return { $args -> c.getFormatString(path, $args) }""").build())
            .addFunction(
                FunSpec.builder("invoke").addModifiers(KModifier.OPERATOR)
                .addParameters(params).returns(configFullFormatString)
                .addCode("""return %T(path, $args)""", configFullFormatString).build())
            .build())
    }

    fun buildYaml(): YamlBlock = YamlBlock(mutableMapOf()).apply { rootBlock.build().yamlAppend(this) }
}