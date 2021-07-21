package gregc.gregchess

import gregc.gregchess.chess.component.ChessClock
import gregc.gregchess.chess.component.ComponentConfig
import org.bukkit.configuration.ConfigurationSection

val config: ConfigurationSection get() = GregChess.plugin.config

fun ConfigurationSection.getLocalizedString(path: String, vararg args: Any?) = LocalizedString(this, path, *args)

class LocalizedString(private val section: ConfigurationSection, private val path: String, private vararg val args: Any?) {
    fun get(lang: String): String =
        (section.getString(path) ?: throw IllegalArgumentException(lang + "/" + section.currentPath + "." + path))
        .format(*args.map { if (it is LocalizedString) it.get(lang) else it }.toTypedArray())
}

fun ComponentConfig.initBukkit() {
    this[ChessClock::class] = BukkitClockConfig
}

object BukkitClockConfig: ChessClock.Config {
    override val timeFormat: String
        get() = config.getString("Clock.TimeFormat")!!
}