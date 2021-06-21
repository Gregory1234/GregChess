package gregc.gregchess

import gregc.gregchess.chess.*
import org.bukkit.Material
import org.bukkit.Sound
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

class BukkitSideConfig(override val side: Side, private val rootView: View) : SideConfig, View by rootView {
    override fun pieceName(n: String) = getLocalizedString("Piece", n)
}

class BukkitPieceTypeConfig(override val type: PieceType, private val rootView: View) :
    PieceTypeBukkitConfig, View by rootView {

    override val name get() = getLocalizedString("Name")
    override val char get() = getLocalizedChar("Char")
    override val item get() = BySides { getEnum("Item.${it.standardName}", Material.AIR) }
    override val structure get() = BySides { getEnumList<Material>("Structure.${it.standardName}") }
    override fun sound(s: PieceSound): Sound = getEnum("Sound.${s.standardName}", Sound.BLOCK_STONE_HIT)
}

class BukkitConfig(private val rootView: BukkitView) :
    ErrorConfig, MessageConfig, TitleConfig,
    RequestConfig, ArenasConfig, StockfishConfig,
    BukkitChessConfig, ComponentsConfig, EndReasonConfig, SettingsConfig,
    View by rootView {

    override fun getError(s: String) = getLocalizedString("Message.Error.$s")

    override val accept get() = getLocalizedString("Request.Accept")
    override val cancel get() = getLocalizedString("Request.Cancel")
    override val selfAccept get() = getDefaultBoolean("Request.SelfAccept", true)

    override fun getRequestType(t: String) = BukkitRequestTypeConfig(t, this["Request.$t"])

    override val chessArenas get() = getStringList("ChessArenas")

    override fun getBukkitPieceType(p: PieceType) = BukkitPieceTypeConfig(p, this["Chess.Piece.${p.standardName}"])

    override fun getSide(s: Side) = BukkitSideConfig(s, this["Chess.Side.${s.standardName}"])

    override val capture get() = getLocalizedString("Chess.Capture")
    override fun floor(f: Floor) = getEnum<Material>("Chess.Floor.${f.standardName}", Material.AIR)

    override val settingsBlocks: Map<String, Map<String, View>>
        get() = this["Settings"].childrenViews.orEmpty().mapValues { it.value.childrenViews.orEmpty() }

    override fun getSettings(n: String): Map<String, View> = this["Settings.$n"].childrenViews.orEmpty()

    override val componentBlocks get() = this["Component"].childrenViews.orEmpty()
    override fun getComponent(n: String) = this["Component.$n"]

    override fun getEndReason(n: String) = getLocalizedString("Chess.EndReason.$n")

    override fun getMessage(s: String, vararg args: Any?) = getLocalizedString("Message.$s", *args)

    override fun getTitle(s: String) = getLocalizedString("Title.$s")

    override val hasStockfish get() = getDefaultBoolean("Chess.HasStockfish", false)
    override val stockfishCommand get() = getString("Chess.Stockfish.Path")
    override val engineName get() = getString("Chess.Stockfish.Name")

}