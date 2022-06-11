package gregc.gregchess.chess

import assertk.assertThat
import assertk.assertions.*
import gregc.gregchess.board.AddVariantOptionsEvent
import gregc.gregchess.board.Chessboard
import gregc.gregchess.byColor
import gregc.gregchess.match.*
import gregc.gregchess.move.connector.AddMoveConnectorsEvent
import gregc.gregchess.move.connector.PieceMoveEvent
import gregc.gregchess.results.EndReason
import gregc.gregchess.results.drawBy
import gregc.gregchess.variant.ChessVariant
import io.mockk.*
import kotlinx.coroutines.isActive
import org.junit.jupiter.api.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChessMatchTests {

    private val playerA = GregChess.TEST_PLAYER.of("A")
    private val playerB = GregChess.TEST_PLAYER.of("B")

    private fun mkMatch(variant: ChessVariant = ChessVariant.Normal, extra: Collection<Component> = emptyList()) =
        ChessMatch(TestChessEnvironment, variant, listOf(Chessboard(variant)) + extra, byColor(playerA, playerB))

    @BeforeAll
    fun setup() {
        setupRegistry()
    }

    @Nested
    inner class Constructing {
        @Test
        fun `should pass components through`() {
            val cd = spyk(TestComponent)
            val g = mkMatch(extra = listOf(cd))
            val c = g.require(GregChess.TEST_COMPONENT)
            assertThat(c).isEqualTo(cd)
        }

        @Test
        fun `should only initialize components`() {
            val cd = spyk(TestComponent)
            val g = mkMatch(extra = listOf(cd))
            val c = g.require(GregChess.TEST_COMPONENT)
            excludeRecords {
                c.type
            }
            verifySequence {
                c.init(g)
            }
        }

        @Test
        fun `should only construct sides`() {
            val g = mkMatch()
            val mocks = g.sides.toList()
            verify {
                mocks wasNot Called
            }
        }

        @Test
        fun `should not make match run`() {
            val match = mkMatch()

            assertThat(match::running).isFalse()
        }
    }

    @Nested
    inner class Starting {
        @Test
        fun `should enable running`() {
            val match = mkMatch().start()

            assertThat(match::running).isTrue()
        }

        @Test
        fun `should throw when already running`() {
            val match = mkMatch().start()

            assertThrows<IllegalStateException> {
                match.start()
            }
        }

        @Test
        fun `should throw when stopped`() {
            val match = mkMatch().start()

            match.stop(drawBy(EndReason.DRAW_AGREEMENT))

            assertThrows<IllegalStateException> {
                match.start()
            }
        }

        @Test
        fun `should start components`() {
            val g = mkMatch(extra = listOf(spyk(TestComponent)))
            val c = g.require(GregChess.TEST_COMPONENT)
            clearRecords(c)
            g.start()
            excludeRecords {
                c.type
                c.handleEvent(match { it is PieceMoveEvent })
                c.handleEvent(match { it is AddVariantOptionsEvent })
                c.handleEvent(match { it is AddMoveConnectorsEvent })
            }
            verifySequence {
                c.handleEvent(ChessBaseEvent.START)
                c.handleEvent(ChessBaseEvent.RUNNING)
                c.handleEvent(TurnEvent.START)
            }
        }

        @Test
        fun `should start turn`() {
            val g = mkMatch().start()
            verifyAll {
                g.sides.white.startTurn()
            }
        }
    }

    @Nested
    inner class Stopping {
        private val results = drawBy(EndReason.DRAW_AGREEMENT)

        @Test
        fun `should disable running`() {
            val match = mkMatch().start()

            match.stop(results)

            assertThat(match::running).isFalse()
        }

        @Test
        fun `should cancel coroutine scope`() {
            val match = mkMatch().start()

            match.stop(results)

            assertThat(match.coroutineScope::isActive).isFalse()
        }

        @Test
        fun `should stop and clear sides`() {
            val g = mkMatch().start()

            clearRecords(g.sides.white)
            clearRecords(g.sides.black)

            g.stop(results)

            verifyAll {
                g.sides.white.stop()
                g.sides.black.stop()
                g.sides.white.clear()
                g.sides.black.clear()
            }
        }

        @Test
        fun `should stop and clear components`() {
            val g = mkMatch(extra = listOf(spyk(TestComponent))).start()
            val c = g.require(GregChess.TEST_COMPONENT)
            clearRecords(c)
            g.stop(results)
            excludeRecords {
                c.type
            }
            verifySequence {
                c.handleEvent(ChessBaseEvent.STOP)
                c.handleEvent(ChessBaseEvent.CLEAR)
            }
        }

        @Test
        fun `should preserve results`() {
            val match = mkMatch().start()

            match.stop(results)

            assertThat(match::results).isNotNull().isSameAs(results)
        }

        @Test
        fun `should throw when not started`() {
            val match = mkMatch()

            assertThrows<IllegalStateException> {
                match.stop(results)
            }
        }

        @Test
        fun `should throw when already stopped`() {
            val match = mkMatch().start()

            match.stop(results)

            assertThrows<IllegalStateException> {
                match.stop(results)
            }
        }
    }
}