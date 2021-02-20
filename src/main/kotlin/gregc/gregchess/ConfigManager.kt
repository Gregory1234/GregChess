package gregc.gregchess

import org.bukkit.plugin.java.JavaPlugin

class ConfigManager(private val plugin: JavaPlugin) {
    private val config
        get() = plugin.config

    private inline fun <T> get(path: String, type: String, default: T, parser: (String) -> T?): T{
        val str = config.getString(path)
        if (str == null) {
            plugin.logger.warning("Not found $type $path, defaulted to $default!")
            return default
        }
        val ret = parser(str)
        if (ret == null) {
            plugin.logger.warning("${type.capitalize()} $path is in a wrong format, defaulted to $default!")
            return default
        }
        return ret
    }

    fun getString(path: String) = get(path, "string", path) {it}

    fun getDuration(path: String) = get(path, "duration", 0.seconds, ::parseDuration)

    fun getError(name: String) = get("Message.Error.$name", "error", name) {it}
}