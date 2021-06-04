package gregc.gregchess

import org.bukkit.configuration.file.FileConfiguration
import kotlin.reflect.KClass

infix fun String.addDot(other: String) = if (isNotEmpty() && other.isNotEmpty()) "$this.$other" else "$this$other"

abstract class ConfigPath<out T>(val path: String = ""){
    fun childPath(ad: String) = path addDot ad
    abstract fun get(c: Configurator): T
}

open class ConfigBlock<out Self: ConfigBlock<Self>>(path: String): ConfigPath<View<ConfigBlock<Self>>>(path) {
    override fun get(c: Configurator): View<ConfigBlock<Self>> = View(this, c)
}

class ConfigBlockList<out P: ConfigBlock<P>>(path: String, private val mk: (ConfigBlock<P>) -> P): ConfigPath<Map<String, View<P>>>(path) {
    override fun get(c: Configurator): Map<String, View<P>> = c.getChildren(path).orEmpty().mapNotNull {
        if (c.isSection(childPath(it))) it to View(mk(ConfigBlock(childPath(it))), c) else null
    }.toMap()
    operator fun get(s: String): P = mk(ConfigBlock((childPath(s))))
}

class ConfigFullFormatString(path: String, private vararg val gotten: Any?): ConfigPath<String>(path) {
    override fun get(c: Configurator): String = c.getFormatString(path, *gotten)
}

class ConfigEnum<T: Enum<T>>(path: String, private val default: T, private val warnMissing: Boolean = true)
    : ConfigPath<T>(path) {
    override fun get(c: Configurator): T = c.getEnum(path, default, warnMissing)
}

class ConfigEnumList<T: Enum<T>>(path: String, private val cl: KClass<T>, private val warnMissing: Boolean = true)
    : ConfigPath<List<T>>(path) {
    override fun get(c: Configurator): List<T> = c.getEnumList(path, cl, warnMissing)
}

interface Configurator {
    fun getString(path: String): String?
    fun getStringList(path: String): List<String>?
    fun getChildren(path: String): Set<String>?
    operator fun contains(path: String): Boolean
    fun isSection(path: String): Boolean
}

class BukkitConfigurator(private val file: FileConfiguration): Configurator {
    override fun getString(path: String): String? = file.getString(path)

    override fun getStringList(path: String): List<String>? = if (path in file) file.getStringList(path) else null

    override fun getChildren(path: String): Set<String>? = file.getConfigurationSection(path)?.getKeys(false)

    override fun contains(path: String): Boolean = path in file

    override fun isSection(path: String): Boolean = file.getConfigurationSection(path) != null

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

fun Configurator.getFormatString(path: String, vararg args: Any?) = get(path, "format string", path) {
    numberedFormat(it, *args)?.let(::chatColor)
}

class View<out P: ConfigBlock<P>>(private val path: P, private val config: Configurator) {
    fun <T> get(f: P.() -> ConfigPath<T>): T = path.f().get(config)
}