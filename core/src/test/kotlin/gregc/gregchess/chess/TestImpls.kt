package gregc.gregchess.chess

import gregc.gregchess.GregChessModule
import gregc.gregchess.chess.component.*
import gregc.gregchess.chess.player.ChessPlayer
import gregc.gregchess.chess.player.ChessPlayerType
import gregc.gregchess.chess.variant.ChessVariant
import gregc.gregchess.register
import io.mockk.clearMocks
import io.mockk.spyk
import kotlinx.serialization.builtins.serializer

fun testSettings(
    name: String, variant: ChessVariant = ChessVariant.Normal,
    extra: List<ComponentData<*>> = emptyList()
): GameSettings {
    val components = buildList {
        this += ChessboardState(variant)
        this.addAll(extra)
    }
    return GameSettings(name, false, variant, components)
}

class TestPlayer(name: String, color: Color, game: ChessGame) : ChessPlayer<String>(name, color, name, game)

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

fun setupRegistry() = with(GregChessModule) {
    register("test", ChessPlayerType(String.serializer()) { c, g -> TestPlayer(this, c, g) })
}