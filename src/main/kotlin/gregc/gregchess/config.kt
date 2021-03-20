package gregc.gregchess

import java.lang.IllegalArgumentException
import kotlin.reflect.KClass

object ConfigManager : View("")

open class View protected constructor(private val rootPath: String = "") {
    private val config
        get() = GregInfo.plugin.config.getConfigurationSection(rootPath)!!

    fun getView(path: String): View? {// TODO: this should never return null!
        val section = config.getConfigurationSection(path) ?: return null
        return View(section.currentPath!!)
    }

    val keys: List<String>
        get() = config.getKeys(false).toList()

    fun toMap() = config.getValues(false).mapValues { (_, v) -> v.toString() }

    fun <T> get(
        path: String,
        type: String,
        default: T,
        warnMissing: Boolean = true,
        parser: (String) -> T?
    ): T {
        val str = config.getString(path)
        val fullPath =
            (if (config.currentPath.orEmpty() == "") "" else (config.currentPath.orEmpty() + ".")) + path
        if (str == null) {
            if (warnMissing)
                glog.warn("Not found $type $fullPath, defaulted to $default!")
            return default
        }
        val ret = parser(str)
        if (ret == null) {
            glog.warn("${type.capitalize()} $fullPath is in a wrong format, defaulted to $default!")
            return default
        }
        return ret
    }

    fun <T> getList(
        path: String,
        type: String,
        warnMissing: Boolean = true,
        parser: (String) -> T?
    ): List<T> {
        val fullPath =
            (if (config.currentPath.orEmpty() == "") "" else (config.currentPath.orEmpty() + ".")) + path
        if (path !in config) {
            if (warnMissing)
                glog.warn("Not found list of $type $fullPath, defaulted to an empty list!")
            return emptyList()
        }
        val str = config.getStringList(path)
        return str.mapNotNull {
            val ret = parser(it)
            if (ret == null) {
                glog.warn("${type.capitalize()} $fullPath is in a wrong format, ignored!")
                null
            } else
                ret
        }

    }

    fun getString(path: String) = get(path, "string", path) { chatColor(it) }

    fun getChar(path: String) = get(path, "char", ' ') { if (it.length == 1) it[0] else null }

    fun getFormatString(path: String, vararg args: Any?) =
        get(path, "format string", path) {
            numberedFormat(it, *args)?.let(::chatColor)
        }

    fun getDuration(path: String, warnMissing: Boolean = true) =
        get(path, "duration", 0.seconds, warnMissing, ::parseDuration)

    fun getError(name: String) = get("Message.Error.$name", "error", name) { chatColor(it) }

    inline fun <reified T : Enum<T>> getEnum(
        path: String,
        default: T,
        cl: KClass<T>,
        warnMissing: Boolean = true
    ) =
        get(path, cl.simpleName?.decapitalize() ?: "enum", default, warnMissing) {
            try {
                enumValueOf(it.toUpperCase())
            } catch (e: IllegalArgumentException) {
                null
            }
        }

    inline fun <reified T : Enum<T>> getEnumList(
        path: String,
        cl: KClass<T>,
        warnMissing: Boolean = true
    ) =
        getList(path, cl.simpleName?.decapitalize() ?: "enum", warnMissing) {
            try {
                enumValueOf<T>(it.toUpperCase())
            } catch (e: IllegalArgumentException) {
                null
            }
        }

    fun getOptionalString(path: String): String? =
        get(path, "optional string", null, false) { chatColor(it) }

    fun getOptionalDuration(path: String) = get(path, "duration", null, false, ::parseDuration)

    fun getHexString(path: String) = get(path, "hex string", null) { hexToBytes(it) }

    fun getBool(path: String, default: Boolean) = get(path, "boolean", default) { it.toBoolean() }

    fun getStringList(path: String) = getList(path, "string") { chatColor(it) }
}