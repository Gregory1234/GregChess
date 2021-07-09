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

class BukkitRequestTypeConfig(override val name: String, private val rootView: View) :
    RequestTypeConfig, View by rootView {

    override fun expired(a1: String) = getLocalizedString("Expired", a1)
    override val duration get() = getOptionalDuration("Duration")

    override val sentRequest get() = getLocalizedString("Sent.Request")
    override fun sentCancel(a1: String) = getLocalizedString("Sent.Cancel", a1)
    override fun sentAccept(a1: String) = getLocalizedString("Sent.Accept", a1)

    override fun receivedRequest(a1: String, a2: String) = getLocalizedString("Received.Request", a1, a2)
    override fun receivedCancel(a1: String) = getLocalizedString("Received.Cancel", a1)
    override fun receivedAccept(a1: String) = getLocalizedString("Received.Accept", a1)

    override val notFound get() = getLocalizedString("Error.NotFound")
    override val cannotSend get() = getLocalizedString("Error.CannotSend")

}

class BukkitConfig(private val rootView: BukkitView) :
    ErrorConfig, MessageConfig, TitleConfig,
    RequestConfig, ArenasConfig, StockfishConfig,
    ComponentsConfig, SettingsConfig,
    View by rootView {

    override fun getError(s: String) = getLocalizedString("Message.Error.$s")

    override val accept get() = getLocalizedString("Request.Accept")
    override val cancel get() = getLocalizedString("Request.Cancel")
    override val selfAccept get() = getDefaultBoolean("Request.SelfAccept", true)

    override fun getRequestType(t: String) = BukkitRequestTypeConfig(t, this["Request.$t"])

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