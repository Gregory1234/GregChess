package gregc.gregchess.config

import com.squareup.kotlinpoet.ClassName

const val PACKAGE_NAME: String = "gregc.gregchess"

val sideType = ClassName("gregc.gregchess.chess", "Side")
val configurator = ClassName("gregc.gregchess", "Configurator")
val configPath = ClassName("gregc.gregchess", "ConfigPath")
val configVal = ClassName("gregc.gregchess", "ConfigVal")
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

fun binaryStrings(n: UInt): List<List<Boolean>> {
    if (n == 0u) return listOf(emptyList())
    if (n == 1u) return listOf(listOf(false), listOf(true))
    val prev = binaryStrings(n - 1u)
    return prev.map {listOf(false) + it} + prev.map {listOf(true) + it}
}