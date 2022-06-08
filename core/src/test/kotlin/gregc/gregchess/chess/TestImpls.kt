package gregc.gregchess.chess

import gregc.gregchess.*
import gregc.gregchess.match.*
import gregc.gregchess.player.ChessPlayerType
import gregc.gregchess.player.ChessSide
import gregc.gregchess.registry.Registry
import gregc.gregchess.results.DetEndReason
import gregc.gregchess.results.EndReason
import gregc.gregchess.variant.ChessVariant
import io.mockk.clearMocks
import io.mockk.spyk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import java.time.*
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock


class TestChessSide(name: String, color: Color, match: ChessMatch) : ChessSide<String>(GregChess.TEST_PLAYER, name, color, match) {
    override fun startTurn() {}

    override fun stop() {}

    override fun clear() {}
}

@Serializable
object TestComponent : Component {

    override val type = GregChess.TEST_COMPONENT

    override fun init(match: ChessMatch) {}

    override fun handleEvent(e: ChessEvent) {}

}

object TestVariant : ChessVariant()

object TestChessEnvironment : ChessEnvironment {
    override val pgnSite: String get() = "GregChess test"
    @OptIn(ExperimentalCoroutinesApi::class)
    override val coroutineDispatcher: CoroutineDispatcher get() = UnconfinedTestDispatcher()
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

        GregChessCore.autoRegister(this).registerAll<GregChess>()
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