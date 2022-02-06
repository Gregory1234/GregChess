package gregc.gregchess.chess

import assertk.assertThat
import assertk.assertions.*
import gregc.gregchess.chess.component.*
import gregc.gregchess.chess.piece.PieceEvent
import gregc.gregchess.chess.variant.ChessVariant
import io.mockk.*
import kotlinx.coroutines.isActive
import org.junit.jupiter.api.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChessGameTests {

    private val playerA = TestPlayer("A")
    private val playerB = TestPlayer("B")

    private fun mkGame(variant: ChessVariant = ChessVariant.Normal, extra: Collection<Component> = emptyList()) =
        ChessGame(TestChessEnvironment, variant, listOf(Chessboard(variant)) + extra, byColor(playerA, playerB))

    @BeforeAll
    fun setup() {
        setupRegistry()
    }

    @Nested
    inner class Constructing {
        @Test
        fun `should pass components through`() {
            val cd = spyk(TestComponent)
            val g = mkGame(extra = listOf(cd))
            val c = g.getComponent<TestComponent>()!!
            assertThat(c).isEqualTo(cd)
        }

        @Test
        fun `should only initialize components`() {
            val cd = spyk(TestComponent)
            val g = mkGame(extra = listOf(cd))
            val c = g.getComponent<TestComponent>()!!
            verifySequence {
                c.init(g)
            }
        }

        @Test
        fun `should only construct sides`() {
            val g = mkGame()
            val mocks = g.sides.toList()
            verify {
                mocks wasNot Called
            }
        }

        @Test
        fun `should not make game run`() {
            val game = mkGame()

            assertThat(game::running).isFalse()
        }
    }

    @Nested
    inner class Starting {
        @Test
        fun `should enable running`() {
            val game = mkGame().start()

            assertThat(game::running).isTrue()
        }

        @Test
        fun `should throw when already running`() {
            val game = mkGame().start()

            assertThrows<IllegalStateException> {
                game.start()
            }
        }

        @Test
        fun `should throw when stopped`() {
            val game = mkGame().start()

            game.stop(drawBy(EndReason.DRAW_AGREEMENT))

            assertThrows<IllegalStateException> {
                game.start()
            }
        }

        @Test
        fun `should start components`() {
            val g = mkGame(extra = listOf(spyk(TestComponent)))
            val c = g.getComponent<TestComponent>()!!
            clearRecords(c)
            g.start()
            excludeRecords {
                c.handleEvent(match { it is PieceEvent })
                c.handleEvent(match { it is AddVariantOptionsEvent })
            }
            verifySequence {
                c.handleEvent(GameBaseEvent.START)
                c.handleEvent(GameBaseEvent.RUNNING)
                c.handleEvent(TurnEvent.START)
            }
        }

        @Test
        fun `should start sides and start turn`() {
            val g = mkGame().start()
            verifyAll {
                g.sides.white.start()
                g.sides.black.start()
                g.sides.white.startTurn()
            }
        }
    }

    @Nested
    inner class Stopping {
        private val results = drawBy(EndReason.DRAW_AGREEMENT)

        @Test
        fun `should disable running`() {
            val game = mkGame().start()

            game.stop(results)

            assertThat(game::running).isFalse()
        }

        @Test
        fun `should cancel coroutine scope`() {
            val game = mkGame().start()

            game.stop(results)

            assertThat(game.coroutineScope::isActive).isFalse()
        }

        @Test
        fun `should stop and clear sides`() {
            val g = mkGame().start()

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
            val g = mkGame(extra = listOf(spyk(TestComponent))).start()
            val c = g.getComponent<TestComponent>()!!
            clearRecords(c)
            g.stop(results)
            verifySequence {
                c.handleEvent(GameBaseEvent.STOP)
                c.handleEvent(GameBaseEvent.CLEAR)
            }
        }

        @Test
        fun `should preserve results`() {
            val game = mkGame().start()

            game.stop(results)

            assertThat(game::results).isNotNull().isSameAs(results)
        }

        @Test
        fun `should throw when not started`() {
            val game = mkGame()

            assertThrows<IllegalStateException> {
                game.stop(results)
            }
        }

        @Test
        fun `should throw when already stopped`() {
            val game = mkGame().start()

            game.stop(results)

            assertThrows<IllegalStateException> {
                game.stop(results)
            }
        }
    }
}