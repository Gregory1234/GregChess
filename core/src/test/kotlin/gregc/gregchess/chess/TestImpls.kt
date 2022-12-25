package gregc.gregchess.chess

import gregc.gregchess.Color
import gregc.gregchess.GregChessCore
import gregc.gregchess.component.Component
import gregc.gregchess.component.ComponentType
import gregc.gregchess.event.ChessEvent
import gregc.gregchess.event.EventListenerRegistry
import gregc.gregchess.match.*
import gregc.gregchess.player.*
import gregc.gregchess.registry.ChessModule
import gregc.gregchess.registry.Register
import gregc.gregchess.registry.registry.Registry
import gregc.gregchess.results.DetEndReason
import gregc.gregchess.results.EndReason
import gregc.gregchess.variant.ChessVariant
import io.mockk.clearMocks
import io.mockk.spyk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.serialization.Serializable
import java.time.*
import java.util.*
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

class TestChessPlayer(private val spy: Boolean) : ChessPlayer<TestChessSide> {
    override fun createChessSide(color: Color): TestChessSide =
        TestChessSide(color.name, color).let { if (spy) spyk(it) else it }
}

@Serializable
class TestChessSide(override val name: String, override val color: Color) : ChessSide {
    override val type get() = GregChess.TEST_SIDE

    override fun init(match: ChessMatch, events: EventListenerRegistry) {
        events.registerAny(::handleEvent)
    }

    @Suppress("UNUSED_PARAMETER")
    fun handleEvent(e: ChessEvent) {}

    override fun createFacade(match: ChessMatch) = TestChessSideFacade(match, this)
}

class TestChessSideFacade(match: ChessMatch, side: TestChessSide) : ChessSideFacade<TestChessSide>(match, side)

@Serializable
object TestComponent : Component {

    override val type get() = GregChess.TEST_COMPONENT

    override fun init(match: ChessMatch, events: EventListenerRegistry) {
        events.registerAny(::handleEvent)
    }

    @Suppress("UNUSED_PARAMETER")
    fun handleEvent(e: ChessEvent) {}

}

object TestVariant : ChessVariant()

object TestChessEnvironment : ChessEnvironment {
    @OptIn(ExperimentalCoroutinesApi::class)
    override val coroutineDispatcher: CoroutineDispatcher get() = UnconfinedTestDispatcher()
    override val clock: Clock get() = Clock.fixed(Instant.EPOCH, ZoneId.systemDefault())
}

class TestMatchInfo(val uuid: UUID = UUID.randomUUID()) : MatchInfo {
    override val pgnSite: String get() = "GregChess test"
    override val pgnEventName: String get() = "Casual game"
    override val pgnRound: Int get() = 1
    override fun matchCoroutineName(): String = uuid.toString()
    override fun matchToString(): String = "uuid=$uuid"
}

fun clearRecords(m: Any, vararg ms: Any) = clearMocks(m, *ms, answers = false)

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
    val TEST_COMPONENT = ComponentType(TestComponent::class, TestComponent.serializer())

    @JvmField
    @Register("test")
    val TEST_VARIANT = TestVariant

    @JvmField
    @Register("test")
    val TEST_SIDE = ChessSideType(TestChessSide.serializer())

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