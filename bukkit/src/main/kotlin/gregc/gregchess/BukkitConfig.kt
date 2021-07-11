package gregc.gregchess

import gregc.gregchess.chess.SettingsManager
import gregc.gregchess.chess.component.ChessClock
import gregc.gregchess.chess.component.ComponentConfig
import gregc.gregchess.chess.variant.AtomicChess
import gregc.gregchess.chess.variant.ThreeChecks
import org.bukkit.configuration.file.FileConfiguration

fun initBukkitConfig(c: BukkitConfigProvider) {
    config = BukkitView(c, "")
    ComponentConfig[ChessClock::class] = BukkitClockConfig
    SettingsManager += { ThreeChecks.CheckCounter.Settings(it.getDefaultInt("CheckLimit", 3).toUInt()) }
    SettingsManager += { AtomicChess.ExplosionManager.Settings }
}

fun interface BukkitConfigProvider {
    operator fun invoke(): FileConfiguration
}

class BukkitView(val file: BukkitConfigProvider, val root: String) : View {
    override fun getPureString(path: String): String? = file().getString(root addDot path)

    override fun getPureStringList(path: String): List<String>? =
        if (root addDot path in file()) file().getStringList(root addDot path) else null

    override fun processString(s: String): String = chatColor(s)

    override val children: Set<String>?
        get() = file().getConfigurationSection(root)?.getKeys(false)

    override fun getOrNull(path: String): View? =
        if (root addDot path in file()) get(path) else null

    override fun get(path: String): View = BukkitView(file, root addDot path)

    override fun fullPath(path: String): String = root addDot path
}

lateinit var config: BukkitView
    private set

object BukkitClockConfig: ChessClock.Config {
    override val timeFormat: String
        get() = config.getString("Clock.TimeFormat")
}

class LocalizedString(private val view: View, private val path: String, private vararg val args: Any?) {
    fun get(lang: String): String =
        view.getVal(path, "string", lang + "/" + view.fullPath(path), true) { s ->
            val f = s.formatOrNull(*args.map { if (it is LocalizedString) it.get(lang) else it }.toTypedArray())
            f?.let(view::processString)
        }
}