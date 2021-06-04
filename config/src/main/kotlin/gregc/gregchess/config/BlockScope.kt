package gregc.gregchess.config

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import gregc.core.*
import java.io.File
import java.time.Duration
import kotlin.io.path.div

const val PACKAGE_NAME = "gregc.gregchess"

val sideType = ClassName("gregc.gregchess.chess", "Side")
val configurator = Configurator::class.asClassName()
val configPath = ConfigPath::class.asClassName()
val configBlock = ConfigBlock::class.asClassName()
val configBlockList = ConfigBlockList::class.asClassName()
val configFullFormatString = ClassName("gregc.gregchess", "ConfigFullFormatString")
val configEnum = ConfigEnum::class.asClassName()
val configEnumList = ConfigEnumList::class.asClassName()

@DslMarker
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class ConfigDSL

@ConfigDSL
class BlockScope(val config: TypeSpec.Builder, val yamlBlock: YamlBlock) {
    private inline fun withBlock(name: String, noinline block: BlockScope.() -> Unit, stuff: (BlockScope) -> Unit) {
        val b = BlockScope(
            TypeSpec.classBuilder(name)
                .superclass(configBlock.parameterizedBy(ClassName("", name)))
                .primaryConstructor(FunSpec.constructorBuilder().addParameter("path", String::class).build())
                .addSuperclassConstructorParameter("path"), YamlBlock(mutableMapOf()))
        b.block()
        stuff(b)
    }
    private fun addBlockProp(typ: String, name: String, path: String) {
        config.addProperty(
            PropertySpec
                .builder(name, ClassName("", typ))
                .getter(FunSpec.getterBuilder().addCode("return $typ(childPath(\"$path\"))").build()).build()
        )
    }
    fun addBlock(name: String, block: BlockScope.() -> Unit) {
        withBlock(name, block) {
            config.addType(it.config.build())
            addBlockProp(name, name.lowerFirst(), name)
            if (it.yamlBlock.value.isNotEmpty())
                yamlBlock.value[name] = it.yamlBlock
        }
    }
    fun instanceBlock(name: String, block: BlockScope.() -> Unit) {
        val typ = config.propertySpecs.first{ it.name == name.lowerFirst() }.type
        withBlock(name, block) {
            // TODO: check if matches
            if (it.yamlBlock.value.isNotEmpty())
                yamlBlock.value[name] = it.yamlBlock
        }
    }
    fun addFormatString(name: String, default: String? = null, vararg args: TypeName) {
        config.addProperty(PropertySpec
            .builder(name.lowerFirst(), ClassName(PACKAGE_NAME, "ConfigFormatString${args.size}").parameterizedBy(*args))
            .getter(FunSpec.getterBuilder().addCode("return ConfigFormatString${args.size}(childPath(\"$name\"))").build()).build())
        if(default != null)
            yamlBlock.value[name] = YamlText(default)
    }
    fun add(name: String, typName: String, default: String? = null, defaultCode: String? = null, warnMissing: Boolean = default == null) {
        config.addProperty(
            PropertySpec.builder(name.lowerFirst(), ClassName(PACKAGE_NAME, "Config$typName"))
            .getter(FunSpec.getterBuilder()
                .addCode("""return Config$typName(childPath("$name"), $defaultCode, $warnMissing)""").build())
            .build())
        if(default != null)
            yamlBlock.value[name] = YamlText(default)
    }
    fun addString(name: String, default: String? = null) = add(name, "String", default)
    fun addChar(name: String, default: Char? = null) = add(name, "Char", default?.let {"'$it'"}.toString())
    fun addDuration(name: String, default: String? = null, warnMissing: Boolean = default == null) = add(name, "Duration", default.toString(), null, warnMissing)
    fun addDefaultBool(name: String, default: Boolean, warnMissing: Boolean = false) = add(name, "Bool", default.toString(), default.toString(), warnMissing)
    fun addDefaultInt(name: String, default: Int) = add(name, "Int", default.toString(), default.toString())
    fun addEnum(name: String, typ: TypeName, default: String, warnMissing: Boolean = true) {
        config.addProperty(
            PropertySpec.builder(name.lowerFirst(), configEnum.parameterizedBy(typ))
                .getter(FunSpec.getterBuilder()
                    .addCode("""return %T(childPath("$name"), %T.$default, $warnMissing)""", configEnum, typ).build())
                .build())
        yamlBlock.value[name] = YamlText(default)
    }
    fun addOptional(name: String, typName: String, warnMissing: Boolean = false) {
        config.addProperty(
            PropertySpec.builder(name.lowerFirst(), ClassName(PACKAGE_NAME, "ConfigOptional$typName"))
                .getter(FunSpec.getterBuilder().addCode("""return ConfigOptional$typName(childPath("$name"), $warnMissing)""").build())
                .build())
    }
    fun addOptionalString(name: String) = addOptional(name, "String")
    fun addOptionalDuration(name: String) = addOptional(name, "Duration")
    fun addList(name: String, typName: String, default: List<String>? = null, warnMissing: Boolean = true) {
        config.addProperty(
            PropertySpec
                .builder(name.lowerFirst(), ClassName(PACKAGE_NAME, "Config${typName}List"))
                .getter(FunSpec.getterBuilder().addCode("""return Config${typName}List(childPath("$name"), $warnMissing)""").build())
                .build())
        if(!default.isNullOrEmpty())
            yamlBlock.value[name] = YamlList(default.toMutableList())
    }
    fun addStringList(name: String) = addList(name, "String")
    fun addEnumList(name: String, typ: TypeName, default: List<String>? = null, warnMissing: Boolean = true) {
        config.addProperty(
            PropertySpec.builder(name.lowerFirst(), configEnumList.parameterizedBy(typ))
                .getter(FunSpec.getterBuilder().addCode("""return %T(childPath("$name"), %T::class, $warnMissing)""",
                    configEnumList, typ).build())
                .build())
        if(!default.isNullOrEmpty())
            yamlBlock.value[name] = YamlList(default.toMutableList())
    }
    fun inlineBlockList(name: String, block: BlockScope.() -> Unit){
        withBlock(name, block) {
            config.addType(it.config.build())
            config.addProperty(
                PropertySpec
                .builder(name.lowerFirst()+"s", configBlockList.parameterizedBy(ClassName("", name)))
                .getter(FunSpec.getterBuilder().addCode("""%T<$name>(path) { $name(it.path) }""", configBlockList).build()).build())
        }
    }
    fun inlineFiniteBlockList(name: String, vararg values: String, block: BlockScope.() -> Unit){
        withBlock(name, block) {
            config.addType(it.config.build())
            values.forEach { nm ->
                addBlockProp(name, nm.lowerFirst(), nm)
            }
        }
    }
    fun addBlockList(name: String, block: BlockScope.() -> Unit){
        withBlock(name+"Element", block) {
            config.addType(it.config.build())
            config.addProperty(PropertySpec
                .builder(name.lowerFirst(), configBlockList.parameterizedBy(ClassName("", name+"Element")))
                .getter(FunSpec.getterBuilder()
                    .addCode("""return %T<${name}Element>(childPath("$name")) { ${name}Element(it.path) }""", configBlockList)
                    .build()).build())
        }
    }
    private fun by(typ: TypeName, ret: TypeName, values: Map<String, String>, nulls: String? = null) {
        val fn = FunSpec.builder("get")
            .addModifiers(KModifier.OPERATOR)
            .returns(ret)
            .addParameter("e", typ.copy(nullable = nulls != null))
            .beginControlFlow("return when (e)")
        values.forEach { (n,v) ->
            fn.addStatement("%T.$n -> $v", typ)
        }
        if (nulls != null)
            fn.addStatement("null -> $nulls", typ)
        config.addFunction(fn.endControlFlow().build())
    }
    fun bySides(white: String, black: String) {
        val ret = config.propertySpecs.first {it.name == white.lowerFirst()}.type
        by(sideType, ret, mapOf("WHITE" to white.lowerFirst(), "BLACK" to black.lowerFirst()))
    }
    fun byOptSides(white: String, black: String, nulls: String) {
        val ret = config.propertySpecs.first {it.name == white.lowerFirst()}.type
        by(sideType, ret, mapOf("WHITE" to white.lowerFirst(), "BLACK" to black.lowerFirst()), nulls.lowerFirst())
    }
    fun byEnum(typ: TypeName, vararg values: String) {
        val ret = config.propertySpecs.first {it.name == values[0].lowerFirst() }.type
        by(typ, ret, values.associate { it.camelToUpperSnake() to it.lowerFirst() })
    }

}

fun String.camelToUpperSnake(): String {
    val camelRegex = "(?<=[a-zA-Z])[A-Z]".toRegex()
    return camelRegex.replace(this) {
        "_${it.value}"
    }.uppercase()
}

fun String.camelToSpaces(): String {
    val camelRegex = "(?<=[a-zA-Z])[A-Z]".toRegex()
    return camelRegex.replace(this) {
        " ${it.value}"
    }.lowercase()
}

fun String.lowerFirst() = replaceFirstChar { it.lowercaseChar() }

fun FileSpec.Builder.viewExtensions(name: String, typ: TypeName, default: String, parser: CodeBlock) {
    addType(TypeSpec.classBuilder("Config$name")
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
            .addCode("""return c.get(path, "${name.lowerFirst().camelToSpaces()}", default ?: $default, warnMissing, $parser)""").build())
        .build())
    addType(TypeSpec.classBuilder("ConfigOptional$name")
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
    addType(TypeSpec.classBuilder("Config${name}List")
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

fun FileSpec.Builder.formatString(i: Int) {
    val params = (1..i).map {ParameterSpec("a$it", TypeVariableName("T$it"))}
    val lam = LambdaTypeName.get(
        parameters = params,
        returnType = String::class.asTypeName())
    val args = (1..i).joinToString { "a$it" }
    addType(TypeSpec.classBuilder("ConfigFormatString$i")
        .apply { (1..i).forEach { addTypeVariable(TypeVariableName("T$it")) } }
        .superclass(configPath.parameterizedBy(lam))
        .primaryConstructor(FunSpec.constructorBuilder().addParameter("path", String::class).build())
        .addSuperclassConstructorParameter("path")
        .addFunction(FunSpec.builder("get").addModifiers(KModifier.OVERRIDE)
            .addParameter("c", configurator).returns(lam)
            .addCode("""return { $args -> c.getFormatString(path, $args) }""").build())
        .addFunction(FunSpec.builder("invoke").addModifiers(KModifier.OPERATOR)
            .addParameters(params).returns(configFullFormatString)
            .addCode("""return %T(path, $args)""", configFullFormatString).build())
        .build())
}



fun config(generatedRoot: File, block: BlockScope.() -> Unit) {
    val c = BlockScope(TypeSpec.objectBuilder("Config")
        .superclass(configBlock.parameterizedBy(ClassName(PACKAGE_NAME, "Config")))
        .addSuperclassConstructorParameter("\"\""), YamlBlock(mutableMapOf()))
    c.block()
    if(!generatedRoot.exists())
        generatedRoot.createNewFile()
    FileSpec.builder(PACKAGE_NAME, "Config")
        .addImport("gregc.core",
            "get", "getList", "getEnum", "getEnumList", "seconds", "parseDuration")
        .apply {
            viewExtensions("String", String::class.asTypeName(), "path", CodeBlock.of("::chatColor"))
            viewExtensions("Bool", Boolean::class.asTypeName(), "true", CodeBlock.of("%T::toBooleanStrictOrNull", String::class))
            viewExtensions("Duration", Duration::class.asTypeName(), "0.seconds", CodeBlock.of("::parseDuration"))
            viewExtensions("Char", Char::class.asTypeName(), "' '", CodeBlock.of("{ if (it.length == 1) it[0] else null }"))
            viewExtensions("Int", Int::class.asTypeName(), "0", CodeBlock.of("%T::toIntOrNull", String::class))
            (1..2).forEach(this::formatString)
        }
        .addType(c.config.build())
        .build()
        .writeTo(generatedRoot)
    (generatedRoot.toPath() / "config.yml").toFile().writeText(c.yamlBlock.build())
}