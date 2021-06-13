package gregc.gregchess

import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter

infix fun String.addDot(other: String) = if (isNotEmpty() && other.isNotEmpty()) "$this.$other" else "$this$other"

fun interface ConfigVal<out T> {
    fun get(c: Configurator): T
}

class ConstVal<out T>(val value: T) : ConfigVal<T> {
    override fun get(c: Configurator): T = value
}

fun <T, R> ConfigVal<T>.map(f: Configurator.(T) -> R): ConfigVal<R> = ConfigVal { it.f(get(it)) }

abstract class ConfigPath<out T>(val path: String = "") : ConfigVal<T> {
    fun childPath(ad: String) = path addDot ad
}

open class ConfigBlock<out Self : ConfigBlock<Self>>(path: String) : ConfigPath<View<ConfigBlock<Self>>>(path) {
    override fun get(c: Configurator): View<ConfigBlock<Self>> = View(this, c)
}

class ConfigBlockList<out P : ConfigBlock<P>>(path: String, private val mk: (ConfigBlock<P>) -> P) :
    ConfigPath<Map<String, View<P>>>(path) {
    override fun get(c: Configurator): Map<String, View<P>> =
        c.getChildren(path).orEmpty().filter { c.isSection(childPath(it)) }
            .associateWith { View(mk(ConfigBlock(childPath(it))), c) }

    operator fun get(s: String): P = mk(ConfigBlock((childPath(s))))
}

class ConfigTimeFormatFull(path: String, private val time: LocalTime, private val warnMissing: Boolean = true) :
    ConfigPath<String>(path) {
    override fun get(c: Configurator): String = c.get(path, "time format", path, warnMissing) {
        time.format(DateTimeFormatter.ofPattern(it))
    }
}

class ConfigTimeFormat(path: String, private val warnMissing: Boolean = true) : ConfigPath<TimeFormat>(path) {
    override fun get(c: Configurator): TimeFormat =
        c.get(path, "time format", TimeFormat(path), warnMissing) { TimeFormat(it) }

    operator fun invoke(time: LocalTime) = ConfigTimeFormatFull(path, time, warnMissing)
    operator fun invoke(time: Duration) = ConfigTimeFormatFull(path, time.toLocalTime(), warnMissing)
}

interface Configurator {
    fun getString(path: String): String?
    fun getStringList(path: String): List<String>?
    fun getChildren(path: String): Set<String>?
    operator fun contains(path: String): Boolean
    fun isSection(path: String): Boolean
    fun processString(s: String): String
}

fun <T> Configurator.get(path: String, type: String, default: T, warnMissing: Boolean = true, parser: (String) -> T?): T {
    val str = getString(path)
    if (str == null) {
        if (warnMissing) {
            glog.warn("Not found $type $path, defaulted to $default!")
        }
        return default
    }
    val ret = parser(str)
    if (ret == null) {
        glog.warn("${type.upperFirst()} $path is in a wrong format, defaulted to $default!")
        return default
    }
    return ret
}

fun <T> Configurator.getList(path: String, type: String, warnMissing: Boolean = true, parser: (String) -> T?): List<T> {
    val str = getStringList(path)
    if (str == null) {
        if (warnMissing)
            glog.warn("Not found list of $type $path, defaulted to an empty list!")
        return emptyList()
    }
    return str.mapNotNull {
        val ret = parser(it)
        if (ret == null) {
            glog.warn("${type.lowerFirst()} $path is in a wrong format, ignored!")
            null
        } else
            ret
    }

}

fun Configurator.getFormatString(path: String, vararg args: Any?) = get(path, "format string", path) {
    numberedFormat(it, *args)?.let(::processString)
}

class ConfigFullFormatString(path: String, private vararg val gotten: Any?) : ConfigPath<String>(path) {
    override fun get(c: Configurator): String =
        c.getFormatString(path, *gotten.map { if (it is ConfigVal<*>) it.get(c) else it }.toTypedArray())
}

class View<out P : ConfigBlock<P>>(private val path: P, private val config: Configurator) {
    fun <T> get(f: P.() -> ConfigPath<T>): T = path.f().get(config)
}