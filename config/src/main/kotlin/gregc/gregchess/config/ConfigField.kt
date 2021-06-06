package gregc.gregchess.config

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy

sealed class ConfigField {

    data class Value(val name: String, val type: ConfigGeneralState.ConfigType,
                     val default: String?, val defaultCode: String?, val warnMissing: Boolean): ConfigField() {
        override fun kotlinAppend(b: TypeSpec.Builder) {
            b.addProperty(PropertySpec
                .builder(name.lowerFirst(), type.toKotlinType())
                .getter(FunSpec.getterBuilder()
                    .addCode("""return %T(childPath("$name"), $defaultCode, $warnMissing)""", type.toKotlinType())
                    .build())
                .build())
        }

        override fun yamlAppend(b: YamlBlock) {
            default?.let {
                b.value[name] = YamlText(it)
            }
        }
    }

    data class ValueList(val name: String, val type: ConfigGeneralState.ConfigType,
                         val default: List<String>?, val warnMissing: Boolean): ConfigField() {
        override fun kotlinAppend(b: TypeSpec.Builder) {
            b.addProperty(PropertySpec
                .builder(name.lowerFirst(), type.toKotlinListType())
                .getter(FunSpec.getterBuilder()
                    .addCode("""return %T(childPath("$name"), $warnMissing)""", type.toKotlinListType())
                    .build())
                .build())
        }

        override fun yamlAppend(b: YamlBlock) {
            default?.let {
                b.value[name] = YamlList(it.toMutableList())
            }
        }
    }

    data class ValueOptional(val name: String, val type: ConfigGeneralState.ConfigType, val warnMissing: Boolean): ConfigField() {
        override fun kotlinAppend(b: TypeSpec.Builder) {
            b.addProperty(PropertySpec
                .builder(name.lowerFirst(), type.toKotlinOptionalType())
                .getter(FunSpec.getterBuilder()
                    .addCode("""return %T(childPath("$name"), $warnMissing)""", type.toKotlinOptionalType())
                    .build())
                .build())
        }

        override fun yamlAppend(b: YamlBlock) {
        }
    }

    data class ValueFormat(val name: String, val inputs: Collection<TypeName>,
                           val default: String?, val warnMissing: Boolean): ConfigField() {
        override fun kotlinAppend(b: TypeSpec.Builder) {
            val typ = ClassName(PACKAGE_NAME, "ConfigFormatString${inputs.size}").parameterizedBy(inputs.toList())
            b.addProperty(PropertySpec
                .builder(name.lowerFirst(), typ)
                .getter(FunSpec.getterBuilder().addCode("""return %T(childPath("$name"))""", typ).build())
                .build())
        }

        override fun yamlAppend(b: YamlBlock) {
            default?.let {
                b.value[name] = YamlText(it)
            }
        }
    }

    data class ValueSpecial(val name: String, val typ: TypeName, val default: String?, val warnMissing: Boolean?): ConfigField() {
        override fun kotlinAppend(b: TypeSpec.Builder) {
            b.addProperty(PropertySpec
                .builder(name.lowerFirst(), typ)
                .getter(FunSpec.getterBuilder().addCode("""return %T(childPath("$name"), ${warnMissing ?: ""})""", typ).build())
                .build())
        }

        override fun yamlAppend(b: YamlBlock) {
            default?.let {
                b.value[name] = YamlText(it)
            }
        }
    }

    data class EnumString(val name: String, val typ: TypeName, val default: String, val warnMissing: Boolean): ConfigField() {
        override fun kotlinAppend(b: TypeSpec.Builder) {
            b.addProperty(PropertySpec
                .builder(name.lowerFirst(), configEnum.parameterizedBy(typ))
                .getter(FunSpec.getterBuilder()
                    .addCode("""return %T(childPath("$name"), %T.$default, $warnMissing)""", configEnum.parameterizedBy(typ), typ)
                    .build())
                .build())
        }

        override fun yamlAppend(b: YamlBlock) {
            b.value[name] = YamlText(default)
        }
    }

    data class EnumStringList(val name: String, val typ: TypeName, val default: List<String>?, val warnMissing: Boolean): ConfigField() {
        override fun kotlinAppend(b: TypeSpec.Builder) {
            b.addProperty(PropertySpec
                .builder(name.lowerFirst(), configEnumList.parameterizedBy(typ))
                .getter(FunSpec.getterBuilder()
                    .addCode("""return %T(childPath("$name"), %T::class, $warnMissing)""", configEnumList.parameterizedBy(typ), typ)
                    .build())
                .build())
        }

        override fun yamlAppend(b: YamlBlock) {
            default?.let {
                b.value[name] = YamlList(it.toMutableList())
            }
        }
    }

    data class ObjectBlock(val name: String, val path: String, val realPath: String, val fields: List<ConfigField>): ConfigField() {
        fun toKotlinObject(): TypeSpec = TypeSpec.objectBuilder(name)
            .apply {
                superclass(configBlock.parameterizedBy(ClassName(if (path.none {it == '.'}) PACKAGE_NAME else "", name)))
                addSuperclassConstructorParameter("\"$realPath\"")
                fields.map { it.kotlinAppend(this) }
            }.build()

        override fun kotlinAppend(b: TypeSpec.Builder) {
            b.addType(toKotlinObject())
            if (path.any {it == '.'})
                b.addProperty(PropertySpec.builder(name.lowerFirst(), ClassName("", name))
                    .getter(FunSpec.getterBuilder()
                        .addCode("""return %T""", ClassName("", name))
                        .build())
                    .build())
        }

        override fun yamlAppend(b: YamlBlock) {
            if (path.none {it == '.'}) {
                fields.forEach {
                    it.yamlAppend(b)
                }
            } else {
                b.with(name) {
                    fields.forEach {
                        it.yamlAppend(this)
                    }
                }
            }
        }
    }

    data class ClassBlock(val name: String, val fields: List<ConfigField>): ConfigField() {
        fun toKotlinClass(): TypeSpec = TypeSpec.classBuilder(name)
            .apply {
                superclass(configBlock.parameterizedBy(ClassName("", name)))
                primaryConstructor(FunSpec.constructorBuilder().addParameter("path", String::class).build())
                addSuperclassConstructorParameter("path")
                fields.map { it.kotlinAppend(this) }
            }.build()

        override fun kotlinAppend(b: TypeSpec.Builder) {
            b.addType(toKotlinClass())
            b.addProperty(PropertySpec.builder(name.lowerFirst(), ClassName("", name))
                .getter(FunSpec.getterBuilder()
                    .addCode("""return %T(childPath("$name"))""", ClassName("", name))
                    .build())
                .build())
        }

        override fun yamlAppend(b: YamlBlock) {
        }
    }

    data class FiniteBlockList(val pattern: ClassBlock, val instances: List<ObjectBlock>): ConfigField() {
        override fun kotlinAppend(b: TypeSpec.Builder) {
            b.addType(pattern.toKotlinClass())
            b.addProperties(instances.map {
                PropertySpec.builder(it.name.lowerFirst(), ClassName("", pattern.name))
                    .getter(FunSpec.getterBuilder()
                        .addCode("""return %T(childPath("${it.name}"))""", ClassName("", pattern.name))
                        .build()).build()
            })
        }

        override fun yamlAppend(b: YamlBlock) {
            instances.forEach{
                it.yamlAppend(b)
            }
        }
    }

    data class BlockList(val name: String, val pattern: ClassBlock): ConfigField() {
        override fun kotlinAppend(b: TypeSpec.Builder) {
            b.addType(pattern.toKotlinClass())
            b.addProperty(PropertySpec
                .builder(name.lowerFirst(), configBlockList.parameterizedBy(ClassName("", pattern.name)))
                .getter(FunSpec.getterBuilder()
                    .addCode("""return %T(childPath("$name")) { %T(it.path) }""",
                        configBlockList.parameterizedBy(ClassName("", pattern.name)),
                        ClassName("", pattern.name)).build())
                .build())
        }

        override fun yamlAppend(b: YamlBlock) {
        }
    }

    data class WhenBlock(val typ: TypeName, val nullInstance: String?, val instances: List<Pair<String, String>>): ConfigField() {
        constructor(typ: TypeName, vararg instances: Pair<String, String>): this(typ, null, instances.toList())
        constructor(typ: TypeName, nullInstance: String, vararg instances: Pair<String, String>): this(typ, nullInstance, instances.toList())
        override fun kotlinAppend(b: TypeSpec.Builder) {
            b.addFunction(FunSpec.builder("get")
                .addParameter("e", typ.copy(nullable = nullInstance != null))
                .addModifiers(KModifier.OPERATOR)
                .addCode(CodeBlock.builder()
                    .apply {
                        beginControlFlow("return when(e)")
                        instances.forEach { (n, v) ->
                            addStatement("""%T.$n -> $v""", typ)
                        }
                        nullInstance?.let {
                            addStatement("""null -> $it""")
                        }
                        endControlFlow()
                    }.build())
                .build())
        }

        override fun yamlAppend(b: YamlBlock) {
        }

    }

    abstract fun kotlinAppend(b: TypeSpec.Builder)
    abstract fun yamlAppend(b: YamlBlock)
}