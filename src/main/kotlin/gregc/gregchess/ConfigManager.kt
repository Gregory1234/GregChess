package gregc.gregchess

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.plugin.java.JavaPlugin
import java.lang.IllegalArgumentException
import kotlin.reflect.KClass

class ConfigManager(private val plugin: JavaPlugin, private val config: ConfigurationSection = plugin.config) {

    fun getSection(path: String): ConfigManager? {
        val section = config.getConfigurationSection(path) ?: return null
        return ConfigManager(plugin, section)
    }

    fun <T> get(path: String, type: String, default: T, warnMissing: Boolean = true, parser: (String) -> T?): T{
        val str = config.getString(path)
        val fullPath = (if (config.currentPath.orEmpty()=="") "" else (config.currentPath.orEmpty()+"."))+path
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

    fun getString(path: String) = get(path, "string", path) {it}

    fun getDuration(path: String) = get(path, "duration", 0.seconds, true, ::parseDuration)

    fun getError(name: String) = get("Message.Error.$name", "error", name) {it}

    inline fun <reified T: Enum<T>> getEnum(path: String, default: T, cl: KClass<T>, warnMissing: Boolean = true) = get(path, cl.simpleName?.decapitalize() ?: "enum", default, warnMissing) {
        try {
            enumValueOf(it.toUpperCase())
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}