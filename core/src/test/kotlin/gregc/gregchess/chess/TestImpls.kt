package gregc.gregchess.chess

import gregc.gregchess.chess.component.*
import gregc.gregchess.chess.variant.ChessVariant
import io.mockk.clearMocks
import io.mockk.spyk

fun testSettings(
    name: String, board: String? = null, variant: ChessVariant = ChessVariant.Normal,
    extra: List<ComponentData<*>> = emptyList()
): GameSettings {
    val components = buildList {
        this += ChessboardState[variant, board]
        this.addAll(extra)
    }
    return GameSettings(name, false, variant, components)
}

val String.cpi get() = TestPlayerInfo(this)

data class TestPlayerInfo(override val name: String): ChessPlayerInfo {
    override fun getPlayer(color: Color, game: ChessGame): ChessPlayer = TestPlayer(this, color, game)
}

class TestPlayer(info: TestPlayerInfo, color: Color, game: ChessGame) : ChessPlayer(info, color, game)

object TestComponentData : ComponentData<TestComponent> {
    override val componentClass = TestComponent::class

    override fun getComponent(game: ChessGame): TestComponent = spyk(TestComponent(game, this))
}

class TestComponent(game: ChessGame, override val data: TestComponentData) : Component(game) {

    override fun validate() {}

    override fun handleEvent(e: ChessEvent) {}

}

object TestVariant : ChessVariant()

@JvmField
val TEST_END_REASON = DetEndReason(EndReason.Type.EMERGENCY)

fun clearRecords(m: Any) = clearMocks(m, answers = false)

inline fun <T> measureTime(block: () -> T): T {
    val start = System.nanoTime()
    val ret = block()
    val elapsed = System.nanoTime() - start
    println("Elapsed time: ${elapsed.toDouble() / 1_000_000}ms")
    return ret
}