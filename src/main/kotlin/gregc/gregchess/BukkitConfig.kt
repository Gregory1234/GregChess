package gregc.gregchess

import gregc.gregchess.chess.*
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.configuration.file.FileConfiguration
import java.time.Duration

fun Config.initBukkit(c: BukkitConfigProvider) {
    this += BukkitConfig(BukkitView(c, ""))
}

fun interface BukkitConfigProvider {
    operator fun invoke(): FileConfiguration
}

class BukkitView(val file: BukkitConfigProvider, val root: String): View {
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

class BukkitConfig(private val rootView: BukkitView):
    ErrorConfig, MessageConfig, TitleConfig,
    RequestConfig, ArenasConfig,
    BukkitChessConfig, ComponentsConfig, EndReasonConfig, BukkitPieceConfig, SettingsConfig, SideConfig,
    View by rootView {

    override fun getError(s: String): String = getString("Message.Error.$s")

    override val accept: String
        get() = getString("Request.Accept")
    override val cancel: String
        get() = getString("Request.Cancel")
    override val selfAccept: Boolean
        get() = getDefaultBoolean("Request.SelfAccept", true)

    private fun request(t: String) = this["Request.$t"]

    override fun getExpired(t: String): (String) -> String = request(t).getStringFormatF1("Expired")

    override fun getRequestDuration(t: String): Duration? = request(t).getOptionalDuration("Duration")

    override fun getSentRequest(t: String): String = request(t).getString("Sent.Request")

    override fun getSentCancel(t: String): (String) -> String = request(t).getStringFormatF1("Sent.Cancel")

    override fun getSentAccept(t: String): (String) -> String = request(t).getStringFormatF1("Sent.Accept")

    override fun getReceivedRequest(t: String): (String, String) -> String =
        request(t).getStringFormatF2("Received.Request")

    override fun getReceivedCancel(t: String): (String) -> String = request(t).getStringFormatF1("Received.Cancel")

    override fun getReceivedAccept(t: String): (String) -> String = request(t).getStringFormatF1("Received.Accept")

    override fun getNotFound(t: String): String = request(t).getString("Error.NotFound")

    override fun getCannotSend(t: String): String = request(t).getString("Error.CannotSend")

    override val chessArenas: List<String>
        get() = getStringList("ChessArenas")

    private fun piece(t: PieceType) = this["Chess.Piece.${t.standardName}"]

    override fun getPieceName(t: PieceType): String = piece(t).getString("Name")

    override fun getPieceChar(t: PieceType): Char = piece(t).getChar("Char")

    override fun getPieceItem(t: PieceType): BySides<Material> =
        BySides { piece(t).getEnum("Item.${it.standardName}", Material.AIR) }

    override fun getPieceStructure(t: PieceType): BySides<List<Material>> =
        BySides { piece(t).getEnumList("Structure.${it.standardName}") }

    override fun getPieceSound(t: PieceType, s: PieceSound): Sound =
        piece(t).getEnum("Sound.${s.standardName}", Sound.BLOCK_STONE_HIT)

    override fun getSidePieceName(s: Side, n: String): String =
        getStringFormat("Chess.Side.${s.standardName}.Piece", n)

    override val capture: String
        get() = getString("Chess.Capture")

    override fun getFloor(f: Floor): Material = getEnum("Chess.Floor.${f.standardName}", Material.AIR)

    override val settingsBlocks: Map<String, Map<String, View>>
        get() = this["Settings"].childrenViews.orEmpty().mapValues { it.value.childrenViews.orEmpty() }

    override fun getSettings(n: String): Map<String, View> = this["Settings.$n"].childrenViews.orEmpty()

    override val componentBlocks: Map<String, View>
        get() = this["Component"].childrenViews.orEmpty()

    override fun getComponent(n: String): View = this["Component.$n"]

    override fun getEndReason(n: String): String = getString("Chess.EndReason.$n")

    override fun getMessage(s: String): String = getString("Message.$s")

    override fun getMessage1(s: String): (String) -> String = getStringFormatF1("Message.$s")

    override fun getTitle(s: String): String = getString("Title.$s")

}