package gregc.gregchess.config

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import java.io.File
import java.time.Duration
import kotlin.io.path.div

const val PACKAGE_NAME = "gregc.gregchess"

val viewClass = ClassName("gregc.gregchess", "View")
val sideType = ClassName("gregc.gregchess.chess", "Side")

@DslMarker
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class ConfigDSL

@ConfigDSL
class BlockScope(val config: TypeSpec.Builder, val yamlBlock: YamlBlock) {
    private inline fun withBlock(name: String, noinline block: BlockScope.() -> Unit, stuff: (BlockScope) -> Unit) {
        val b = BlockScope(
            TypeSpec.classBuilder(name)
                .superclass(viewClass)
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
        val fn = FunSpec.builder("get$name").returns(String::class)
        args.forEachIndexed { i, v ->
            fn.addParameter("a${i+1}", v)
        }
        fn.addCode("return getFormatString(\"$name\"")
        args.forEachIndexed { i, _ ->
            fn.addCode(", a${i+1}")
        }
        fn.addCode(")")
        config.addFunction(fn.build())
        if(default != null)
            yamlBlock.value[name] = YamlText(default)
    }
    fun add(name: String, typ: TypeName, typName: String, default: String? = null, defaultCode: String? = null, warnMissing: Boolean = true) {
        config.addProperty(
            PropertySpec.builder(name.lowerFirst(), typ)
            .getter(FunSpec.getterBuilder().addCode("return get$typName(\"$name\", $defaultCode, $warnMissing)").build())
            .build())
        if(default != null)
            yamlBlock.value[name] = YamlText(default)
    }
    fun addString(name: String, default: String? = null) = add(name, String::class.asTypeName(), "String", default)
    fun addChar(name: String, default: Char? = null) = add(name, Char::class.asTypeName(), "Char", default?.let {"'$it'"}.toString())
    fun addDuration(name: String, default: String? = null, warnMissing: Boolean = true) = add(name, Duration::class.asTypeName(), "Duration", default.toString(), null, warnMissing)
    fun addEnum(name: String, typ: TypeName, default: String, warnMissing: Boolean = true) = add(name, typ, "Enum", default, "$typ.$default", warnMissing)
    fun addDefaultBool(name: String, default: Boolean, warnMissing: Boolean = false) = add(name, Boolean::class.asTypeName(), "Bool", default.toString(), default.toString(), warnMissing)
    fun addDefaultInt(name: String, default: Int) = add(name, Int::class.asTypeName(), "Int", default.toString(), default.toString())
    fun addOptional(name: String, typ: TypeName, typName: String, warnMissing: Boolean = false) {
        config.addProperty(
            PropertySpec.builder(name.lowerFirst(), typ.copy(nullable = true))
                .getter(FunSpec.getterBuilder().addCode("return getOptional$typName(\"$name\", $warnMissing)").build())
                .build())
    }
    fun addOptionalString(name: String) = addOptional(name, String::class.asTypeName(), "String")
    fun addOptionalDuration(name: String) = addOptional(name, Duration::class.asTypeName(), "Duration")
    fun addList(name: String, typ: TypeName, typName: String, default: List<String>? = null, warnMissing: Boolean = true) {
        config.addProperty(
            PropertySpec
                .builder(name.lowerFirst(), List::class.asClassName().parameterizedBy(typ))
                .getter(FunSpec.getterBuilder().addCode("return get${typName}List(\"$name\", $warnMissing)").build())
                .build())
        if(!default.isNullOrEmpty())
            yamlBlock.value[name] = YamlList(default.toMutableList())
    }
    fun addStringList(name: String) = addList(name, String::class.asTypeName(), "String")
    fun addEnumList(name: String, typ: TypeName, default: List<String>? = null, warnMissing: Boolean = true) = addList(name, typ, "Enum", default, warnMissing)
    fun inlineBlockList(name: String, block: BlockScope.() -> Unit){
        withBlock(name, block) {
            config.addType(it.config.build())
            config.addProperty(
                PropertySpec
                .builder(name.lowerFirst()+"s",
                    Map::class.asClassName().parameterizedBy(String::class.asClassName(), ClassName("", name)))
                .getter(FunSpec.getterBuilder().addCode("return children.mapValues{ $name(childPath(it.key)) }").build()).build())
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
            config.addProperty(
                PropertySpec
                    .builder(name.lowerFirst(),
                        Map::class.asClassName().parameterizedBy(String::class.asClassName(), ClassName("", name+"Element")))
                    .getter(FunSpec.getterBuilder().addCode("return with(getView(\"$name\")) { children.mapValues{ ${name}Element(childPath(it.key)) } }").build()).build())
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
        by(ClassName("gregc.gregchess.chess", "Side"), ret, mapOf("WHITE" to white.lowerFirst(), "BLACK" to black.lowerFirst()))
    }
    fun byOptSidesFormat(white: String, black: String, nulls: String) {
        val ret = config.funSpecs.first {it.name == "get" + white}
        by(ClassName("gregc.gregchess.chess", "Side"),
            LambdaTypeName.get(parameters = ret.parameters, returnType = ret.returnType!!), mapOf("WHITE" to "::get$white", "BLACK" to "::get$black"), "::get$nulls")
    }
    fun byOptSides(white: String, black: String, nulls: String) {
        val ret = config.propertySpecs.first {it.name == white.lowerFirst()}.type
        by(ClassName("gregc.gregchess.chess", "Side"), ret, mapOf("WHITE" to white.lowerFirst(), "BLACK" to black.lowerFirst()), nulls.lowerFirst())
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

fun viewExtensions(name: String, typ: TypeName, default: String, parser: CodeBlock): List<FunSpec> = listOf(
    FunSpec.builder("get$name").returns(typ).receiver(viewClass)
        .addParameter("path", String::class)
        .addParameter(ParameterSpec.builder("default", typ.copy(nullable = true)).defaultValue("null").build())
        .addParameter(ParameterSpec.builder("warnMissing", Boolean::class).defaultValue("default == null").build())
        .addCode("""return get<%T>(path, "${name.camelToSpaces()}", default ?: $default, warnMissing, $parser)""", typ).build(),
    FunSpec.builder("getOptional$name").returns(typ.copy(nullable = true)).receiver(viewClass)
        .addParameter("path", String::class)
        .addParameter(ParameterSpec.builder("warnMissing", Boolean::class).defaultValue("false").build())
        .addCode("""return get<%T>(path, "${name.camelToSpaces()}", null, warnMissing, $parser)""", typ.copy(nullable = true)).build(),
    FunSpec.builder("get${name}List").returns(List::class.asTypeName().parameterizedBy(typ)).receiver(viewClass)
        .addParameter("path", String::class)
        .addParameter(ParameterSpec.builder("warnMissing", Boolean::class).defaultValue("true").build())
        .addCode("""return getList<%T>(path, "${name.camelToSpaces()}", warnMissing, $parser)""", typ).build(),
)



fun config(generatedRoot: File, block: BlockScope.() -> Unit) {
    val c = BlockScope(TypeSpec.objectBuilder("Config")
        .superclass(viewClass)
        .addSuperclassConstructorParameter("\"\""), YamlBlock(mutableMapOf()))
    c.block()
    if(!generatedRoot.exists())
        generatedRoot.createNewFile()
    FileSpec.builder(PACKAGE_NAME, "Config")
        .apply {
            viewExtensions("String", String::class.asTypeName(), "path",
                CodeBlock.of("::chatColor")).forEach{
                addFunction(it)
            }
            viewExtensions("Bool", Boolean::class.asTypeName(), "true", CodeBlock.of("%T::toBooleanStrictOrNull", String::class)).forEach{
                addFunction(it)
            }
            viewExtensions("Duration", Duration::class.asTypeName(), "0.seconds", CodeBlock.of("::parseDuration")).forEach{
                addFunction(it)
            }
            viewExtensions("Char", Char::class.asTypeName(), "' '", CodeBlock.of("{ if (it.length == 1) it[0] else null }")).forEach{
                addFunction(it)
            }
            viewExtensions("Int", Int::class.asTypeName(), "0", CodeBlock.of("%T::toIntOrNull", String::class)).forEach{
                addFunction(it)
            }
        }
        .addType(c.config.build())
        .build()
        .writeTo(generatedRoot)
    (generatedRoot.toPath() / "config.yml").toFile().writeText(c.yamlBlock.build())
}