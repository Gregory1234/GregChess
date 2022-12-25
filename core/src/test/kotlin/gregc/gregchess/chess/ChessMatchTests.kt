package gregc.gregchess.chess

import assertk.assertThat
import assertk.assertions.*
import gregc.gregchess.board.Chessboard
import gregc.gregchess.component.Component
import gregc.gregchess.event.ChessBaseEvent
import gregc.gregchess.event.TurnEvent
import gregc.gregchess.match.ChessMatch
import gregc.gregchess.match.ChessTimeManager
import gregc.gregchess.move.connector.AddMoveConnectorsEvent
import gregc.gregchess.move.connector.PieceMoveEvent
import gregc.gregchess.player.ChessSideManager
import gregc.gregchess.results.EndReason
import gregc.gregchess.results.drawBy
import gregc.gregchess.variant.ChessVariant
import io.mockk.*
import kotlinx.coroutines.isActive
import org.junit.jupiter.api.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChessMatchTests {

    private fun mkMatch(variant: ChessVariant = ChessVariant.Normal, variantOptions: Long = 0, extra: Collection<Component> = emptyList(), playerManager: ChessSideManager = ChessSideManager(TestChessPlayer(false), TestChessPlayer(false))) =
        ChessMatch(TestChessEnvironment, TestMatchInfo(), variant, listOf(playerManager, Chessboard(variant, variantOptions), ChessTimeManager()) + extra, variantOptions)

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
            val c = g.components.require(GregChess.TEST_COMPONENT)
            assertThat(c).isEqualTo(cd)
        }

        @Test
        fun `should only initialize components`() {
            val cd = spyk(TestComponent)
            val g = mkMatch(extra = listOf(cd))
            val c = g.components.require(GregChess.TEST_COMPONENT)
            excludeRecords {
                c.type
            }
            verifySequence {
                c.init(g, any())
            }
        }

        @Test
        fun `should only initialize sides`() {
            val g = mkMatch(playerManager = ChessSideManager(TestChessPlayer(true), TestChessPlayer(true)))
            val w = g.sides.white.side as TestChessSide
            val b = g.sides.black.side as TestChessSide
            excludeRecords {
                w.type
                w.name
                w.color
                b.type
                b.name
                b.color
            }
            verifyAll {
                w.init(g, any())
                b.init(g, any())
                w.createFacade(g)
                b.createFacade(g)
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
            val c = g.components.require(GregChess.TEST_COMPONENT)
            clearRecords(c)
            g.start()
            excludeRecords {
                c.type
                c.handleEvent(match { it is PieceMoveEvent })
                c.handleEvent(match { it is AddMoveConnectorsEvent })
            }
            verifySequence {
                c.handleEvent(ChessBaseEvent.START)
                c.handleEvent(ChessBaseEvent.RUNNING)
                c.handleEvent(TurnEvent.START)
            }
        }

        @Test
        fun `should start sides`() {
            val g = mkMatch(playerManager = ChessSideManager(TestChessPlayer(true), TestChessPlayer(true)))
            val w = g.sides.white.side as TestChessSide
            val b = g.sides.black.side as TestChessSide
            clearRecords(w, b)
            g.start()
            excludeRecords {
                w.type
                w.name
                w.color
                w.handleEvent(match { it is PieceMoveEvent })
                w.handleEvent(match { it is AddMoveConnectorsEvent })
                b.type
                b.name
                b.color
                b.handleEvent(match { it is PieceMoveEvent })
                b.handleEvent(match { it is AddMoveConnectorsEvent })
            }
            verifySequence {
                w.handleEvent(ChessBaseEvent.START)
                b.handleEvent(ChessBaseEvent.START)
                w.handleEvent(ChessBaseEvent.RUNNING)
                b.handleEvent(ChessBaseEvent.RUNNING)
                w.handleEvent(TurnEvent.START)
                b.handleEvent(TurnEvent.START)
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
            val g = mkMatch(playerManager = ChessSideManager(TestChessPlayer(true), TestChessPlayer(true))).start()
            val w = g.sides.white.side as TestChessSide
            val b = g.sides.black.side as TestChessSide
            clearRecords(w, b)
            g.stop(results)
            excludeRecords {
                w.type
                w.name
                w.color
                b.type
                b.name
                b.color
            }
            verifySequence {
                w.handleEvent(ChessBaseEvent.STOP)
                b.handleEvent(ChessBaseEvent.STOP)
                w.handleEvent(ChessBaseEvent.CLEAR)
                b.handleEvent(ChessBaseEvent.CLEAR)
            }
        }

        @Test
        fun `should stop and clear components`() {
            val g = mkMatch(extra = listOf(spyk(TestComponent))).start()
            val c = g.components.require(GregChess.TEST_COMPONENT)
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