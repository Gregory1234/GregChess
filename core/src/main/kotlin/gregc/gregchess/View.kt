package gregc.gregchess

import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.reflect.KClass

infix fun String.addDot(other: String) = if (isNotEmpty() && other.isNotEmpty()) "$this.$other" else "$this$other"

abstract class ConfigPath<out T>(val path: String = ""){
    fun childPath(ad: String) = path addDot ad
    abstract fun get(c: Configurator): T
}

open class ConfigBlock<out Self: ConfigBlock<Self>>(path: String): ConfigPath<View<ConfigBlock<Self>>>(path) {
    override fun get(c: Configurator): View<ConfigBlock<Self>> = View(this, c)
}

class ConfigBlockList<out P: ConfigBlock<P>>(path: String, private val mk: (ConfigBlock<P>) -> P)
    : ConfigPath<Map<String, View<P>>>(path) {
    override fun get(c: Configurator): Map<String, View<P>> = c.getChildren(path).orEmpty().mapNotNull {
        if (c.isSection(childPath(it))) it to View(mk(ConfigBlock(childPath(it))), c) else null
    }.toMap()
    operator fun get(s: String): P = mk(ConfigBlock((childPath(s))))
}

class ConfigEnum<T: Enum<T>>(path: String, private val default: T, private val warnMissing: Boolean = true)
    : ConfigPath<T>(path) {
    override fun get(c: Configurator): T = c.getEnum(path, default, warnMissing)
}

class ConfigEnumList<T: Enum<T>>(path: String, private val cl: KClass<T>, private val warnMissing: Boolean = true)
    : ConfigPath<List<T>>(path) {
    override fun get(c: Configurator): List<T> = c.getEnumList(path, cl, warnMissing)
}

class ConfigTimeFormatFull(path: String, private val time: LocalTime, private val warnMissing: Boolean = true): ConfigPath<String>(path) {
    override fun get(c: Configurator): String = c.get(path, "time format", path, warnMissing) {
        time.format(DateTimeFormatter.ofPattern(it))
    }
}

class ConfigTimeFormat(path: String, private val warnMissing: Boolean = true): ConfigPath<TimeFormat>(path) {
    override fun get(c: Configurator): TimeFormat = c.get(path, "time format", TimeFormat(path), warnMissing) { TimeFormat(it) }
    operator fun invoke(time: LocalTime) = ConfigTimeFormatFull(path, time, warnMissing)
    operator fun invoke(time: Duration) = ConfigTimeFormatFull(path, time.toLocalTime(), warnMissing)
}

interface Configurator {
    fun getString(path: String): String?
    fun getStringList(path: String): List<String>?
    fun getChildren(path: String): Set<String>?
    operator fun contains(path: String): Boolean
    fun isSection(path: String): Boolean
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

fun <T: Enum<T>> Configurator.getEnum(path: String, default: T, warnMissing: Boolean = true) =
    get(path, default::class.simpleName?.lowerFirst() ?: "enum", default, warnMissing) {
        try {
            java.lang.Enum.valueOf(default::class.java, it.uppercase())
        } catch (e: IllegalArgumentException) {
            null
        }
    }

fun <T: Enum<T>> Configurator.getEnumList(path: String, cl: KClass<T>, warnMissing: Boolean = true) =
    getList(path, cl.simpleName?.lowerFirst() ?: "enum", warnMissing) {
        try {
            java.lang.Enum.valueOf(cl.java, it.uppercase())
        } catch (e: IllegalArgumentException) {
            null
        }
    }

class View<out P: ConfigBlock<P>>(private val path: P, private val config: Configurator) {
    fun <T> get(f: P.() -> ConfigPath<T>): T = path.f().get(config)
}