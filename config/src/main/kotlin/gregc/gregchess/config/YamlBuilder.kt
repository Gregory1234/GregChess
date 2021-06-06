package gregc.gregchess.config

import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

const val INDENT: String = "  "

sealed class YamlValue{
    abstract fun build(name: String? = null, indent: Int = 0): String
}
class YamlText(val value: String): YamlValue() {
    private fun escape(): String {
        return if (value.length == 3 && value.first() == '\'' && value.last() == '\'')
            value
        else if (value.all {it.isDigit()})
            value
        else if (value.all {it.isLetter() && it.isUpperCase() || it == '_'})
            value
        else if (value[0].isDigit() && value.dropWhile { it.isDigit() }.all { it.isLetter() && it.isLowerCase() })
            value
        else if (value in listOf("true", "false"))
            value
        else
            "\"$value\""
    }
    override fun build(name: String?, indent: Int) = when(name){
        null -> INDENT.repeat(indent) + "- " + escape() + "\n"
        else -> INDENT.repeat(indent) + name + ": " + escape() + "\n"
    }
}

class YamlList(val value: MutableList<String>): YamlValue() {
    override fun build(name: String?, indent: Int) = buildString {
        if (name == null) {
            value.forEach {
                append(YamlText(it).build(indent = indent))
            }
        } else {
            append(INDENT.repeat(indent), name, ":\n")
            value.forEach {
                append(YamlText(it).build(indent = indent + 1))
            }
        }
    }
}

class YamlBlock(val value: MutableMap<String, YamlValue>): YamlValue() {
    override fun build(name: String?, indent: Int) = buildString {
        if (name == null) {
            value.forEach {
                append(it.value.build(name = it.key, indent = indent))
            }
        } else {
            append(INDENT.repeat(indent), name, ":\n")
            value.forEach {
                append(it.value.build(name = it.key, indent = indent + 1))
            }
        }
    }
    fun with(name: String, function: YamlBlock.() -> Unit) {
        contract {
            callsInPlace(function, InvocationKind.EXACTLY_ONCE)
        }
        if (name !in value || value[name] !is YamlBlock)
            value[name] = YamlBlock(mutableMapOf())
        (value[name] as YamlBlock).function()
    }
}
