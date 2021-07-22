package gregc.gregchess

import org.bukkit.configuration.ConfigurationSection

val config: ConfigurationSection get() = GregChess.plugin.config

val namespacePlugins = mutableMapOf("gregchess" to GregChess.plugin)

fun configOf(namespace: String) = namespacePlugins[namespace]?.config ?: config

fun ConfigurationSection.getLocalizedString(path: String, vararg args: Any?) = LocalizedString(this, path, *args)

class LocalizedString(private val section: ConfigurationSection, val path: String, private vararg val args: Any?) {
    fun get(lang: String): String =
        (section.getString(path) ?: throw IllegalArgumentException(lang + "/" + section.currentPath + "." + path))
        .format(*args.map { if (it is LocalizedString) it.get(lang) else it }.toTypedArray())
}