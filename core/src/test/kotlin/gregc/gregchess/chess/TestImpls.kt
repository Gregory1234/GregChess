package gregc.gregchess.chess

import gregc.gregchess.ChessModule
import gregc.gregchess.GregChessCore
import gregc.gregchess.chess.component.Component
import gregc.gregchess.chess.component.ComponentType
import gregc.gregchess.chess.player.ChessPlayerType
import gregc.gregchess.chess.player.ChessSide
import gregc.gregchess.chess.variant.ChessVariant
import gregc.gregchess.registry.*
import io.mockk.clearMocks
import io.mockk.spyk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import java.time.*
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock


class TestChessSide(name: String, color: Color, game: ChessGame) : ChessSide<String>(GregChess.TEST_PLAYER, name, color, game) {
    override fun start() {}

    override fun startTurn() {}

    override fun stop() {}

    override fun clear() {}
}

@Serializable
object TestComponent : Component {

    override val type = GregChess.TEST_COMPONENT

    override fun init(game: ChessGame) {}

    override fun handleEvent(e: ChessEvent) {}

}

object TestVariant : ChessVariant()

object TestChessEnvironment : ChessEnvironment {
    override val pgnSite: String get() = "GregChess test"
    @OptIn(ExperimentalCoroutinesApi::class)
    override val coroutineDispatcher: CoroutineDispatcher get() = TestCoroutineDispatcher()
    override val clock: Clock get() = Clock.fixed(Instant.EPOCH, ZoneId.systemDefault())
}

fun clearRecords(m: Any) = clearMocks(m, answers = false)

inline fun <T> measureTime(block: () -> T): T {
    val start = System.nanoTime()
    val ret = block()
    val elapsed = System.nanoTime() - start
    println("Elapsed time: ${elapsed.toDouble() / 1_000_000}ms")
    return ret
}

object GregChess : ChessModule("GregChess", "gregchess") {

    var finished = false
        private set

    @JvmField
    @Register("test")
    val TEST_END_REASON = DetEndReason(EndReason.Type.EMERGENCY)

    @JvmField
    @Register("test")
    val TEST_COMPONENT = ComponentType(TestComponent::class)

    @JvmField
    @Register("test")
    val TEST_VARIANT = TestVariant

    @JvmField
    @Register("test")
    val TEST_PLAYER = ChessPlayerType(String.serializer(), { it }) { n, c, g -> spyk(TestChessSide(n, c, g)) }

    override fun postLoad() {
    }

    override fun finish() {
    }

    override fun validate() {
        Registry.REGISTRIES.forEach { it[this].validate() }
    }

    override fun load() {
        GregChessCore.registerAll(this)

        AutoRegister(this, AutoRegister.basicTypes).registerAll<GregChess>()
        finished = true
    }
}
private var started = false
private val lock: Lock = ReentrantLock()
fun setupRegistry() {
    lock.lock()
    if (!started) {
        started = true
        GregChess.fullLoad()
    }
    lock.unlock()
}