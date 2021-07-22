package gregc.gregchess.chess

import gregc.gregchess.TimeManager
import gregc.gregchess.asIdent
import gregc.gregchess.chess.component.Chessboard
import gregc.gregchess.chess.component.Component
import gregc.gregchess.chess.variant.ChessVariant
import io.mockk.clearMocks
import io.mockk.spyk
import java.time.Duration

class TestTimeManager : TimeManager {
    override fun runTaskLater(delay: Duration, callback: () -> Unit) {
        callback()
    }

    override fun runTaskNextTick(callback: () -> Unit) {
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

fun testSettings(
    name: String, board: String? = null, variant: String? = null,
    extra: List<Component.Settings<*>> = emptyList()
): GameSettings {
    val components = buildList {
        this += Chessboard.Settings[board]
        this.addAll(extra)
    }
    return GameSettings(name, false, ChessVariant[variant], components)
}

class TestHuman(name: String): HumanPlayer(name) {

    override fun sendPGN(pgn: PGN) {
    }

    override fun sendFEN(fen: FEN) {
    }

    override fun setItem(i: Int, piece: Piece?) {
    }

    override fun openPawnPromotionMenu(moves: List<MoveCandidate>) {
    }

    override fun showEndReason(side: Side, reason: GameEnd<*>) {
    }

    override fun showEndReason(reason: GameEnd<*>) {
    }

    override fun sendGameUpdate(side: Side, status: List<GamePlayerStatus>) {
    }

    override fun sendLastMoves(num: UInt, wLast: MoveData?, bLast: MoveData?) {
    }

}

class TestComponent : Component {

    object Settings : Component.Settings<TestComponent> {
        override fun getComponent(game: ChessGame): TestComponent = spyk(TestComponent())
    }

    @ChessEventHandler
    @Suppress("UNUSED_PARAMETER")
    fun handleEvents(e: GameBaseEvent) {}

    @ChessEventHandler
    @Suppress("UNUSED_PARAMETER")
    fun handleTurn(e: TurnEvent) {}

    @ChessEventHandler
    @Suppress("UNUSED_PARAMETER")
    fun handlePlayer(p: HumanPlayerEvent) {}

}

object TestVariant: ChessVariant("test")

@JvmField
val TEST_END_REASON = DetEndReason("test".asIdent(), EndReason.Type.EMERGENCY)

fun clearRecords(m: Any) = clearMocks(m, answers = false)