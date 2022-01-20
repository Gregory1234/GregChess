package gregc.gregchess.chess

import gregc.gregchess.*
import gregc.gregchess.chess.component.Chessboard
import gregc.gregchess.chess.component.Component
import gregc.gregchess.chess.player.ChessPlayer
import gregc.gregchess.chess.player.ChessSide
import gregc.gregchess.chess.variant.ChessVariant
import io.mockk.clearMocks
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.Serializable

fun testSettings(
    name: String, variant: ChessVariant = ChessVariant.Normal,
    extra: List<Component> = emptyList()
): GameSettings {
    val components = buildList {
        this += Chessboard(variant)
        this.addAll(extra)
    }
    return GameSettings(name, variant, components)
}

class TestPlayer(override val name: String) : ChessPlayer {
    override fun initSide(color: Color, game: ChessGame): TestChessSide = TestChessSide(this, color, game)
}

class TestChessSide(player: TestPlayer, color: Color, game: ChessGame) : ChessSide<TestPlayer>(player, color, game)

@Serializable
object TestComponent : Component {

    override fun init(game: ChessGame) {}

    override fun handleEvent(e: ChessEvent) {}

}

object TestVariant : ChessVariant()

@JvmField
val TEST_END_REASON = DetEndReason(EndReason.Type.EMERGENCY)

object TestChessEnvironment : ChessEnvironment {
    override val pgnSite: String get() = "GregChess test"
    override val coroutineDispatcher: CoroutineDispatcher get() = throw UnsupportedOperationException()
}

fun clearRecords(m: Any) = clearMocks(m, answers = false)

inline fun <T> measureTime(block: () -> T): T {
    val start = System.nanoTime()
    val ret = block()
    val elapsed = System.nanoTime() - start
    println("Elapsed time: ${elapsed.toDouble() / 1_000_000}ms")
    return ret
}

fun setupRegistry() = with(GregChess) {
    if (!locked) {
        fullLoad(listOf(ChessExtension {
            TEST_END_REASON.register(this, "test")
            TestVariant.register(this, "test")
            registerComponent<TestComponent>("test")
            registerPlayerClass<TestPlayer>("test")
        }))
    }
}