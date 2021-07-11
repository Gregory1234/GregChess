package gregc.gregchess

import gregc.gregchess.chess.StockfishConfig
import org.bukkit.configuration.file.FileConfiguration
import java.time.Duration

fun Config.initBukkit(c: BukkitConfigProvider) {
    this += BukkitConfig(BukkitView(c, ""))
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

class BukkitConfig(private val rootView: BukkitView) : StockfishConfig, TimeFormatConfig, View by rootView {

    override val hasStockfish get() = getDefaultBoolean("Chess.HasStockfish", false)
    override val stockfishCommand get() = getString("Chess.Stockfish.Path")
    override val engineName get() = getString("Chess.Stockfish.Name")

    override fun formatTime(time: Duration) = getTimeFormat("TimeFormat", time)

}

val config: BukkitConfig
    get() = Config.get()!!

class LocalizedString(private val view: View, private val path: String, private vararg val args: Any?) {
    fun get(lang: String): String =
        view.getVal(path, "string", lang + "/" + view.fullPath(path), true) { s ->
            view.processString(s.format(*args.map { if (it is LocalizedString) it.get(lang) else it }.toTypedArray()))
        }
}