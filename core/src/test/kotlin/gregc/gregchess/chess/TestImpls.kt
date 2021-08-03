package gregc.gregchess.chess

import gregc.gregchess.chess.component.Chessboard
import gregc.gregchess.chess.component.Component
import gregc.gregchess.chess.variant.ChessVariant
import io.mockk.clearMocks
import io.mockk.spyk

fun testSettings(
    name: String, board: String? = null, variant: ChessVariant = ChessVariant.Normal,
    extra: List<Component.Settings<*>> = emptyList()
): GameSettings {
    val components = buildList {
        this += Chessboard.Settings[board]
        this.addAll(extra)
    }
    return GameSettings(name, false, variant, components)
}

class TestPlayer(name: String, side: Side, game: ChessGame) : ChessPlayer(name, side, game)

fun ChessGame.AddPlayersScope.test(name: String, side: Side) {
    addPlayer(TestPlayer(name, side, game))
}

class TestComponent : Component {

    object Settings : Component.Settings<TestComponent> {
        override fun getComponent(game: ChessGame): TestComponent = spyk(TestComponent())
    }

    @ChessEventHandler
    @Suppress("UNUSED_PARAMETER")
    fun handleEvents(e: GameBaseEvent) {
    }

    @ChessEventHandler
    @Suppress("UNUSED_PARAMETER")
    fun handleTurn(e: TurnEvent) {
    }

}

object TestVariant : ChessVariant("TEST")

@JvmField
val TEST_END_REASON = DetEndReason("TEST", EndReason.Type.EMERGENCY)

fun clearRecords(m: Any) = clearMocks(m, answers = false)

inline fun <T> measureTime(block: () -> T): T {
    val start = System.nanoTime()
    val ret = block()
    val elapsed = System.nanoTime() - start
    println("Elapsed time: ${elapsed.toDouble() / 1_000_000}ms")
    return ret
}