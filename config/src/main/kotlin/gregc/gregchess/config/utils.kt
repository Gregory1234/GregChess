package gregc.gregchess.config

import com.squareup.kotlinpoet.ClassName

const val PACKAGE_NAME: String = "gregc.gregchess"

val sideType = ClassName("gregc.gregchess.chess", "Side")
val configurator = ClassName("gregc.gregchess", "Configurator")
val configPath = ClassName("gregc.gregchess", "ConfigPath")
val configBlock = ClassName("gregc.gregchess", "ConfigBlock")
val configBlockList = ClassName("gregc.gregchess", "ConfigBlockList")
val configFullFormatString = ClassName("gregc.gregchess", "ConfigFullFormatString")
val configEnum = ClassName("gregc.gregchess", "ConfigEnum")
val configEnumList = ClassName("gregc.gregchess", "ConfigEnumList")

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

infix fun String.addDot(other: String) = if (this.isEmpty()) other else "$this.$other"