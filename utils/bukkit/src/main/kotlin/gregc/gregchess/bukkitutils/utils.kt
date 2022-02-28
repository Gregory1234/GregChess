package gregc.gregchess.bukkitutils

import org.bukkit.*
import org.bukkit.command.CommandSender
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player

class Message(val config: ConfigurationSection, val path: String) {
    fun get() = config.getPathString(path)
}

fun CommandSender.sendMessage(msg: Message) = sendMessage(msg.get())

fun Player.sendTitleFull(title: String?, subtitle: String?) = sendTitle(title, subtitle, 10, 70, 20)

fun ConfigurationSection.getPathString(path: String, vararg args: String) =
    getString(path)?.format(*args)?.chatColor() ?: ((currentPath ?: "") + "-" + path)

fun String.chatColor(): String = ChatColor.translateAlternateColorCodes('&', this)

class CommandException(val error: Message, cause: Throwable? = null) : Exception(cause) {

    override val message: String get() = "Uncaught command error: ${error.get()}"
}

inline fun cTry(p: CommandSender, err: (Exception) -> Unit = {}, f: () -> Unit) = try {
    f()
} catch (e: CommandException) {
    p.sendMessage(e.error.get())
    err(e)
}

fun getOfflinePlayerByName(name: String): OfflinePlayer? =
    Bukkit.getOfflinePlayers().filter { it.name == name }.maxByOrNull { it.lastPlayed }

fun ConfigurationSection.getOrCreateSection(path: String): ConfigurationSection =
    getConfigurationSection(path) ?: createSection(path)