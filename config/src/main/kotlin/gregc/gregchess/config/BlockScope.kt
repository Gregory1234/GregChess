package gregc.gregchess.config

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import java.time.Duration

val viewClass = ClassName("gregc.gregchess", "View")
val sideType = ClassName("gregc.gregchess.chess", "Side")

@DslMarker
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class ConfigDSL

@ConfigDSL
class BlockScope(val config: TypeSpec.Builder, val yamlBlock: YamlBlock) {
    fun addBlock(name: String, block: BlockScope.() -> Unit) {
        val b = BlockScope(
            TypeSpec.classBuilder(name)
            .superclass(viewClass)
            .primaryConstructor(FunSpec.constructorBuilder().addParameter("path", String::class).build())
            .addSuperclassConstructorParameter("path"), YamlBlock(mutableMapOf()))
        b.block()
        config.addType(b.config.build())
        config.addProperty(
            PropertySpec
            .builder(name.replaceFirstChar { it.lowercaseChar() }, ClassName("", name))
            .getter(FunSpec.getterBuilder().addCode("return $name(childPath(\"$name\"))").build()).build())
        if (b.yamlBlock.value.isNotEmpty())
            yamlBlock.value[name] = b.yamlBlock
    }
    fun instanceBlock(name: String, block: BlockScope.() -> Unit) {
        val typ = config.propertySpecs.first{ it.name == name.replaceFirstChar { it.lowercaseChar() } }.type
        val b = BlockScope(
            TypeSpec.classBuilder(name)
            .superclass(viewClass)
            .primaryConstructor(FunSpec.constructorBuilder().addParameter("path", String::class).build())
            .addSuperclassConstructorParameter("path"), YamlBlock(mutableMapOf()))
        b.block()
        // TODO: check if matches
        if (b.yamlBlock.value.isNotEmpty())
            yamlBlock.value[name] = b.yamlBlock
    }
    fun addString(name: String, default: String? = null) {
        config.addProperty(
            PropertySpec
            .builder(name.replaceFirstChar { it.lowercaseChar() }, String::class)
            .getter(FunSpec.getterBuilder().addCode("return getString(\"$name\")").build()).build())
        if(default != null)
            yamlBlock.value[name] = YamlText(default)
    }
    fun addOptionalString(name: String) {
        config.addProperty(
            PropertySpec
            .builder(name.replaceFirstChar { it.lowercaseChar() }, String::class.asClassName().copy(nullable = true))
            .getter(FunSpec.getterBuilder().addCode("return getOptionalString(\"$name\")").build()).build())
    }
    fun addFormatString(name: String, default: String? = null, vararg args: TypeName) {
        val fn = FunSpec.builder("get$name").returns(String::class)
        args.forEachIndexed { i, v ->
            fn.addParameter("a${i+1}", v)
        }
        fn.addCode("return getFormatString(\"\"")
        args.forEachIndexed { i, _ ->
            fn.addCode(", a${i+1}")
        }
        fn.addCode(")")
        config.addFunction(fn.build())
        if(default != null)
            yamlBlock.value[name] = YamlText(default)
    }
    fun addStringList(name: String) {
        config.addProperty(
            PropertySpec
            .builder(name.replaceFirstChar { it.lowercaseChar() }, List::class.asClassName().parameterizedBy(String::class.asClassName()))
            .getter(FunSpec.getterBuilder().addCode("return getStringList(\"$name\")").build())
            .build())
    }
    fun addBool(name: String, default: Boolean, warnMissing: Boolean = false) {
        config.addProperty(
            PropertySpec
            .builder(name.replaceFirstChar { it.lowercaseChar() }, Boolean::class)
            .getter(FunSpec.getterBuilder().addCode("return getBool(\"$name\", $default, $warnMissing)").build())
            .build())
        yamlBlock.value[name] = YamlText(default.toString())
    }
    fun addChar(name: String, default: Char? = null) {
        config.addProperty(
            PropertySpec
            .builder(name.replaceFirstChar { it.lowercaseChar() }, Char::class)
            .getter(FunSpec.getterBuilder().addCode("return getChar(\"$name\")").build())
            .build())
        if(default != null)
            yamlBlock.value[name] = YamlText("'$default'")
    }
    fun addEnum(name: String, typ: TypeName, default: String, warnMissing: Boolean = true) {
        config.addProperty(
            PropertySpec
            .builder(name.replaceFirstChar { it.lowercaseChar() }, typ)
            .getter(FunSpec.getterBuilder().addCode("return getEnum<%T>(\"$name\", %T.$default, $warnMissing)", typ, typ).build())
            .build())
        yamlBlock.value[name] = YamlText(default)
    }
    fun addEnumList(name: String, typ: TypeName, default: List<String>? = null, warnMissing: Boolean = true) {
        config.addProperty(
            PropertySpec
            .builder(name.replaceFirstChar { it.lowercaseChar() }, List::class.asClassName().parameterizedBy(typ))
            .getter(FunSpec.getterBuilder().addCode("return getEnumList<%T>(\"$name\", $warnMissing)", typ).build())
            .build())
        if(!default.isNullOrEmpty())
            yamlBlock.value[name] = YamlList(default.toMutableList())
    }
    fun addDuration(name: String, default: String? = null, warnMissing: Boolean = true) {
        config.addProperty(
            PropertySpec
            .builder(name.replaceFirstChar { it.lowercaseChar() }, Duration::class)
            .getter(FunSpec.getterBuilder().addCode("return getDuration(\"$name\", $warnMissing)").build())
            .build())
        if(default != null)
            yamlBlock.value[name] = YamlText(default)
    }
    fun addOptionalDuration(name: String) {
        config.addProperty(
            PropertySpec
            .builder(name.replaceFirstChar { it.lowercaseChar() }, Duration::class.asClassName().copy(nullable = true))
            .getter(FunSpec.getterBuilder().addCode("return getOptionalDuration(\"$name\")").build())
            .build())
    }
    fun addDefaultInt(name: String, default: Int, warnMissing: Boolean = true) {
        config.addProperty(
            PropertySpec
            .builder(name.replaceFirstChar { it.lowercaseChar() }, Int::class)
            .getter(FunSpec.getterBuilder().addCode("return getInt(\"$name\", $default, $warnMissing)").build())
            .build())
        yamlBlock.value[name] = YamlText(default.toString())
    }
    fun inlineBlockList(name: String, block: BlockScope.() -> Unit){
        val b = BlockScope(
            TypeSpec.classBuilder(name)
            .superclass(viewClass)
            .primaryConstructor(FunSpec.constructorBuilder().addParameter("path", String::class).build())
            .addSuperclassConstructorParameter("path"), YamlBlock(mutableMapOf()))
        b.block()
        config.addType(b.config.build())
        config.addProperty(
            PropertySpec
            .builder(name.replaceFirstChar { it.lowercaseChar() }+"s",
                Map::class.asClassName().parameterizedBy(String::class.asClassName(), ClassName("", name)))
            .getter(FunSpec.getterBuilder().addCode("return children.mapValues{ $name(childPath(it.key)) }").build()).build())
    }
    fun inlineFiniteBlockList(name: String, vararg values: String, block: BlockScope.() -> Unit){
        val b = BlockScope(
            TypeSpec.classBuilder(name)
            .superclass(viewClass)
            .primaryConstructor(FunSpec.constructorBuilder().addParameter("path", String::class).build())
            .addSuperclassConstructorParameter("path"), YamlBlock(mutableMapOf()))
        b.block()
        config.addType(b.config.build())
        values.forEach { nm ->
            config.addProperty(
                PropertySpec
                .builder(nm.replaceFirstChar { it.lowercaseChar() }, ClassName("", name))
                .getter(FunSpec.getterBuilder().addCode("return $name(childPath(\"$nm\"))").build()).build())
        }
    }
    fun bySides(white: String, black: String) {
        val wt = config.propertySpecs.first {it.name == white.replaceFirstChar { it.lowercaseChar() }}.type
        val bt = config.propertySpecs.first {it.name == black.replaceFirstChar { it.lowercaseChar() }}.type
        require(wt == bt)
        config.addFunction(
            FunSpec.builder("get")
            .addModifiers(KModifier.OPERATOR)
            .returns(wt)
            .addParameter("side", sideType)
            .addCode("""
                return when (side) { 
                    %T.WHITE -> ${white.replaceFirstChar { it.lowercaseChar() }}
                    %T.BLACK -> ${black.replaceFirstChar { it.lowercaseChar() }}
                }
            """.trimIndent(), sideType, sideType)
            .build())
    }
    fun byOptSidesFormat(white: String, black: String, nulls: String) {
        val wt = config.funSpecs.first {it.name == "get$white"}
        val bt = config.funSpecs.first {it.name == "get$black"}
        val nt = config.funSpecs.first {it.name == "get$nulls"}
        require(wt.returnType != null)
        require(wt.returnType == bt.returnType)
        require(wt.returnType == nt.returnType)
        require(wt.parameters.map {it.type} == bt.parameters.map {it.type})
        require(wt.parameters.map {it.type} == nt.parameters.map {it.type})
        config.addFunction(
            FunSpec.builder("get")
            .addModifiers(KModifier.OPERATOR)
            .returns(LambdaTypeName.get(parameters = wt.parameters, returnType = wt.returnType!!))
            .addParameter("side", ClassName("gregc.gregchess.chess", "Side").copy(nullable = true))
            .addCode("""
                return when (side) { 
                    %T.WHITE -> ::get$white
                    %T.BLACK -> ::get$black
                    null -> ::get$nulls
                }
            """.trimIndent(), sideType, sideType)
            .build())
    }
    fun byOptSides(white: String, black: String, nulls: String) {
        val wt = config.propertySpecs.first {it.name == white.replaceFirstChar { it.lowercaseChar() }}.type
        val bt = config.propertySpecs.first {it.name == black.replaceFirstChar { it.lowercaseChar() }}.type
        val nt = config.propertySpecs.first {it.name == nulls.replaceFirstChar { it.lowercaseChar() }}.type
        require(wt == bt)
        require(wt == nt)
        config.addFunction(
            FunSpec.builder("get")
            .addModifiers(KModifier.OPERATOR)
            .returns(wt)
            .addParameter("side", ClassName("gregc.gregchess.chess", "Side").copy(nullable = true))
            .addCode("""
                return when (side) { 
                    %T.WHITE -> ${white.replaceFirstChar { it.lowercaseChar() }}
                    %T.BLACK -> ${black.replaceFirstChar { it.lowercaseChar() }}
                    null -> ${nulls.replaceFirstChar { it.lowercaseChar() }}
                }
            """.trimIndent(), sideType, sideType)
            .build())
    }
    fun byEnum(typ: TypeName, vararg values: String) {
        val ft = config.propertySpecs.first {it.name == values[0].replaceFirstChar { it.lowercaseChar() }}.type
        require(values.all { v ->
            config.propertySpecs.first {it.name == v.replaceFirstChar { it.lowercaseChar() }}.type == ft
        })
        val fn = FunSpec.builder("get")
            .addModifiers(KModifier.OPERATOR)
            .returns(ft)
            .addParameter("e", typ)
            .beginControlFlow("return when (e)")
        values.forEach { v ->
            fn.addStatement("%T.${v.camelToUpperSnake()} -> ${v.replaceFirstChar { it.lowercaseChar() }}", typ)
        }
        config.addFunction(fn.endControlFlow().build())
    }

}

fun String.camelToUpperSnake(): String {
    val camelRegex = "(?<=[a-zA-Z])[A-Z]".toRegex()
    return camelRegex.replace(this) {
        "_${it.value}"
    }.uppercase()
}