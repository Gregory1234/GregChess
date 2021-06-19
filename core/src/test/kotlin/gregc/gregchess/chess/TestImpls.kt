package gregc.gregchess.chess

import gregc.gregchess.*
import gregc.gregchess.chess.component.*
import gregc.gregchess.chess.variant.ChessVariant
import io.mockk.clearMocks
import io.mockk.spyk
import java.time.Duration

class TestTimeManager : TimeManager {
    override fun runTaskLater(delay: Duration, callback: () -> Unit) {
        callback()
    }

    override fun runTaskTimer(delay: Duration, period: Duration, callback: TimeManager.CancellableContext.() -> Unit) {
        (object : TimeManager.CancellableContext {
            override fun cancel() {
            }
        }).callback()
    }

    override fun runTaskAsynchronously(callback: () -> Unit) {
        callback()
    }

}

class TestScoreboard: ScoreboardManager {
    object Settings: Component.Settings<TestScoreboard> {
        override fun getComponent(game: ChessGame) = TestScoreboard()

    }

    override fun plusAssign(p: GameProperty) {
    }

    override fun plusAssign(p: PlayerProperty) {
    }

}

fun testSettings(
    name: String, board: String? = null, clock: String? = null, variant: String? = null,
    extra: List<Component.Settings<*>> = emptyList()
): GameSettings {
    val components = buildList {
        this += Chessboard.Settings[board]
        ChessClock.Settings[clock]?.let { this += it }
        this += TestScoreboard.Settings
        this.addAll(extra)
    }
    return GameSettings(name, false, ChessVariant[variant], components)
}

class TestHuman(name: String): HumanPlayer(name) {
    override var isAdmin: Boolean = false

    override fun sendMessage(msg: String) {
    }

    override fun sendTitle(title: String, subtitle: String) {
    }

    override fun sendPGN(pgn: PGN) {
    }

    override fun sendFEN(fen: FEN) {
    }

    override fun sendCommandMessage(msg: String, action: String, command: String) {
    }

    override fun setItem(i: Int, piece: Piece?) {
    }

    override fun openPawnPromotionMenu(moves: List<MoveCandidate>) {
    }

}

class TestComponent : Component {

    object Settings : Component.Settings<TestComponent> {
        override fun getComponent(game: ChessGame): TestComponent = spyk(TestComponent())
    }

    @GameEvent(GameBaseEvent.INIT)
    fun init() {}

    @GameEvent(GameBaseEvent.START)
    fun start() {}

    @GameEvent(GameBaseEvent.BEGIN)
    fun begin() {}

    @GameEvent(GameBaseEvent.UPDATE)
    fun update() {}

    @GameEvent(GameBaseEvent.SPECTATOR_JOIN)
    fun spectatorJoin(p: HumanPlayer) {}

    @GameEvent(GameBaseEvent.SPECTATOR_LEAVE)
    fun spectatorLeave(p: HumanPlayer) {}

    @GameEvent(GameBaseEvent.STOP)
    fun stop() {}

    @GameEvent(GameBaseEvent.CLEAR)
    fun clear() {}

    @GameEvent(GameBaseEvent.VERY_END)
    fun veryEnd() {}

    @GameEvent(GameBaseEvent.START_TURN)
    fun startTurn() {}

    @GameEvent(GameBaseEvent.END_TURN)
    fun endTurn() {}

    @GameEvent(GameBaseEvent.PRE_PREVIOUS_TURN)
    fun prePreviousTurn() {}

    @GameEvent(GameBaseEvent.START_PREVIOUS_TURN)
    fun startPreviousTurn() {}

    @GameEvent(GameBaseEvent.ADD_PLAYER)
    fun addPlayer(p: HumanPlayer) {}

    @GameEvent(GameBaseEvent.REMOVE_PLAYER)
    fun removePlayer(p: HumanPlayer) {}

    @GameEvent(GameBaseEvent.RESET_PLAYER)
    fun resetPlayer(p: HumanPlayer) {}

    @GameEvent(GameBaseEvent.PANIC)
    fun panic() {}

}

class TestVariant: ChessVariant("test")

val EndReasonConfig.test by EndReasonConfig

class TestEndReason(winner: Side? = null): EndReason(EndReasonConfig::test, "emergency", winner)

class TestView(private val root: String) : View {
    override fun getPureString(path: String): String? = null

    override fun getPureStringList(path: String): List<String>? = null

    override fun processString(s: String): String = s

    override val children: Set<String>?
        get() = null

    override fun getOrNull(path: String): View? = null

    override fun get(path: String): View = TestView(root addDot path)

    override fun fullPath(path: String): String = root addDot path
}

class TestConfig(private val rootView: TestView) :
    ErrorConfig, MessageConfig, TitleConfig, ArenasConfig, RequestConfig,
    ChessConfig, ComponentsConfig, EndReasonConfig, PieceConfig, SettingsConfig, SideConfig,
    View by rootView {
    override fun getError(s: String): String = getString("Message.Error.$s")

    private fun request(t: String) = this["Request.$t"]

    override val chessArenas: List<String>
        get() = getStringList("ChessArenas")

    private fun piece(t: PieceType) = this["Chess.Piece.${t.standardName}"]

    override fun getPieceName(t: PieceType): String = piece(t).getString("Name")

    override fun getPieceChar(t: PieceType): Char = piece(t).getChar("Char")

    override fun getSidePieceName(s: Side, n: String): String =
        getStringFormat("Chess.Side.${s.standardName}.Piece", n)

    override val capture: String
        get() = getString("Chess.Capture")

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

    override val accept: String
        get() = getString("Request.Accept")
    override val cancel: String
        get() = getString("Request.Cancel")
    override val selfAccept: Boolean
        get() = getDefaultBoolean("Request.SelfAccept", true)

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

}

fun Config.initTest() {
    this += TestConfig(TestView(""))
}

fun clearRecords(m: Any) = clearMocks(m, answers = false)