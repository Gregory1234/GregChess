package gregc.gregchess

import org.bukkit.plugin.java.JavaPlugin
import java.lang.Exception
import java.lang.IllegalArgumentException
import kotlin.reflect.KClass

class ConfigManager(private val plugin: JavaPlugin, private val rootPath: String = "") {

    private val config
        get() = plugin.config.getConfigurationSection(rootPath)!!

    fun getSection(path: String): ConfigManager? {
        val section = config.getConfigurationSection(path) ?: return null
        return ConfigManager(plugin, section.currentPath!!)
    }

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
                plugin.logger.warning("Not found $type $fullPath, defaulted to $default!")
            return default
        }
        val ret = parser(str)
        if (ret == null) {
            plugin.logger.warning("${type.capitalize()} $fullPath is in a wrong format, defaulted to $default!")
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
                plugin.logger.warning("Not found list of $type $fullPath, defaulted to an empty list!")
            return emptyList()
        }
        val str = config.getStringList(path)
        return str.mapNotNull {
            val ret = parser(it)
            if (ret == null) {
                plugin.logger.warning("${type.capitalize()} $fullPath is in a wrong format, ignored!")
                null
            } else
                ret
        }

    }

    fun getString(path: String) = get(path, "string", path) { chatColor(it) }

    fun getChar(path: String) = get(path, "char", ' ') { if (it.length == 1) it[0] else null }

    fun getFormatString(path: String, vararg args: Any?) =
        get(path, "format string", path) {
            try {
                chatColor(it.format(*args))
            } catch (e: Exception) {
                plugin.logger.warning(e.stackTraceToString())
                null
            }
        }

    fun getDuration(path: String) = get(path, "duration", 0.seconds, true, ::parseDuration)

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

    fun getHexString(path: String) = get(path, "hex string", null) { hexToBytes(it) }
}