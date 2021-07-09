package gregc.gregchess

import gregc.gregchess.chess.*
import org.bukkit.configuration.file.FileConfiguration

fun Config.initBukkit(c: BukkitConfigProvider) {
    this += BukkitConfig(BukkitView(c, ""))
}

fun interface BukkitConfigProvider {
    operator fun invoke(): FileConfiguration
}

class BukkitView(val file: BukkitConfigProvider, val root: String) : View {
    override fun getPureString(path: String): String? = file().getString(root addDot path)

    override fun getPureLocalizedString(path: String, lang: String): String? = file().getString(root addDot path)

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

class BukkitConfig(private val rootView: BukkitView) :
    ErrorConfig, MessageConfig, TitleConfig, ArenasConfig, StockfishConfig, ComponentsConfig, SettingsConfig,
    View by rootView {

    override fun getError(s: String) = getLocalizedString("Message.Error.$s")

    override val chessArenas get() = getStringList("ChessArenas")

    override val settingsBlocks: Map<String, Map<String, View>>
        get() = this["Settings"].childrenViews.orEmpty().mapValues { it.value.childrenViews.orEmpty() }

    override fun getSettings(n: String): Map<String, View> = this["Settings.$n"].childrenViews.orEmpty()

    override val componentBlocks get() = this["Component"].childrenViews.orEmpty()
    override fun getComponent(n: String) = this["Component.$n"]

    override fun getMessage(s: String, vararg args: Any?) = getLocalizedString("Message.$s", *args)

    override fun getTitle(s: String) = getLocalizedString("Title.$s")

    override val hasStockfish get() = getDefaultBoolean("Chess.HasStockfish", false)
    override val stockfishCommand get() = getString("Chess.Stockfish.Path")
    override val engineName get() = getString("Chess.Stockfish.Name")

}

val config: BukkitConfig
    get() = Config.get()!!