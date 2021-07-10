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
    name: String, board: String? = null, variant: String? = null,
    extra: List<Component.Settings<*>> = emptyList()
): GameSettings {
    val components = buildList {
        this += Chessboard.Settings[board]
        this += TestScoreboard.Settings
        this.addAll(extra)
    }
    return GameSettings(name, false, ChessVariant[variant], components)
}

class TestHuman(name: String): HumanPlayer(name) {

    override fun sendMessage(msg: String) {
    }

    override fun sendTitle(title: String, subtitle: String) {
    }

    override fun sendPGN(pgn: PGN) {
    }

    override fun sendFEN(fen: FEN) {
    }

    override fun setItem(i: Int, piece: Piece?) {
    }

    override fun openPawnPromotionMenu(moves: List<MoveCandidate>) {
    }

    override fun showEndReason(side: Side, reason: EndReason) {
    }

    override fun showEndReason(reason: EndReason) {
    }

    override fun local(msg: LocalizedString): String = msg.get("en_US")

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
    @Suppress("UNUSED_PARAMETER")
    fun spectatorJoin(p: HumanPlayer) {}

    @GameEvent(GameBaseEvent.SPECTATOR_LEAVE)
    @Suppress("UNUSED_PARAMETER")
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
    @Suppress("UNUSED_PARAMETER")
    fun addPlayer(p: HumanPlayer) {}

    @GameEvent(GameBaseEvent.REMOVE_PLAYER)
    @Suppress("UNUSED_PARAMETER")
    fun removePlayer(p: HumanPlayer) {}

    @GameEvent(GameBaseEvent.RESET_PLAYER)
    @Suppress("UNUSED_PARAMETER")
    fun resetPlayer(p: HumanPlayer) {}

    @GameEvent(GameBaseEvent.PANIC)
    fun panic() {}

}

object TestVariant: ChessVariant("test")

class TestEndReason(winner: Side? = null): EndReason("Test", "emergency", winner)

class TestView(private val root: String) : View {
    override fun getPureString(path: String): String? = null

    override fun getPureLocalizedString(path: String, lang: String): String? = null

    override fun getPureStringList(path: String): List<String>? = null

    override fun processString(s: String): String = s

    override val children: Set<String>?
        get() = null

    override fun getOrNull(path: String): View? = null

    override fun get(path: String): View = TestView(root addDot path)

    override fun fullPath(path: String): String = root addDot path
}

class TestConfig(private val rootView: TestView) : MessageConfig, TitleConfig, View by rootView {

    override fun getMessage(s: String, vararg args: Any?) = getLocalizedString("Message.$s", *args)

    override fun getTitle(s: String) = getLocalizedString("Title.$s")

}

fun Config.initTest() {
    this += TestConfig(TestView(""))
}

fun clearRecords(m: Any) = clearMocks(m, answers = false)