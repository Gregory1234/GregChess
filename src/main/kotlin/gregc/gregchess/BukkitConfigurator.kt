package gregc.gregchess

import org.bukkit.ChatColor
import org.bukkit.configuration.file.FileConfiguration

class BukkitConfigurator(private var file: FileConfiguration): Configurator {
    override fun getString(path: String): String? = file.getString(path)

    override fun getStringList(path: String): List<String>? = if (path in file) file.getStringList(path) else null

    override fun getChildren(path: String): Set<String>? = file.getConfigurationSection(path)?.getKeys(false)

    override fun contains(path: String): Boolean = path in file

    override fun isSection(path: String): Boolean = file.getConfigurationSection(path) != null

    override fun processString(s: String) = ChatColor.translateAlternateColorCodes('&', s)

    fun reload(config: FileConfiguration) {
        file = config
    }

}