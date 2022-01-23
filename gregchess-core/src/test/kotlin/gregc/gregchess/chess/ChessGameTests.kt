package gregc.gregchess.chess

import assertk.assertThat
import assertk.assertions.*
import gregc.gregchess.chess.piece.PieceEvent
import io.mockk.*
import kotlinx.coroutines.isActive
import org.junit.jupiter.api.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChessGameTests {

    // TODO: move mocks to initialization to make tests faster
    // TODO: mock Chessboard

    private val playerA = TestPlayer("A")
    private val playerB = TestPlayer("B")

    private val normalSettings = testSettings("normal")

    private fun mkGame(settings: GameSettings) = ChessGame(TestChessEnvironment, settings, byColor(playerA, playerB))

    @BeforeAll
    fun setup() {
        setupRegistry()
    }

    @Nested
    inner class Constructing {
        @Test
        fun `should pass components through`() {
            val cd = spyk(TestComponent)
            val g = mkGame(testSettings("spy", extra = listOf(cd)))
            val c = g.getComponent<TestComponent>()!!
            assertThat(c).isEqualTo(cd)
        }

        @Test
        fun `should only initialize components`() {
            val cd = spyk(TestComponent)
            val g = mkGame(testSettings("spy", extra = listOf(cd)))
            val c = g.getComponent<TestComponent>()!!
            verifySequence {
                c.init(g)
            }
        }

        @Test
        fun `should only construct players`() {
            val g = mkGame(testSettings("spy"))
            val mocks = g.sides.toList()
            verify {
                mocks wasNot Called
            }
        }

        @Test
        fun `should not make game run`() {
            val game = mkGame(normalSettings)

            assertThat(game::running).isFalse()
        }
    }

    @Nested
    inner class Starting {
        @Test
        fun `should enable running`() {
            val game = mkGame(normalSettings).start()

            assertThat(game::running).isTrue()
        }

        @Test
        fun `should throw when already running`() {
            val game = mkGame(normalSettings).start()

            assertThrows<IllegalStateException> {
                game.start()
            }
        }

        @Test
        fun `should throw when stopped`() {
            val game = mkGame(normalSettings).start()

            game.stop(drawBy(EndReason.DRAW_AGREEMENT))

            assertThrows<IllegalStateException> {
                game.start()
            }
        }

        @Test
        fun `should start components`() {
            val g = mkGame(testSettings("spy", extra = listOf(spyk(TestComponent)))).start()
            val c = g.getComponent<TestComponent>()!!
            excludeRecords {
                c.init(g)
                c.handleEvent(match { it is PieceEvent })
            }
            verifySequence {
                c.handleEvent(GameBaseEvent.START)
                c.handleEvent(GameBaseEvent.RUNNING)
                c.handleEvent(TurnEvent.START)
            }
        }

        @Test
        fun `should init players and start turn`() {
            val g = mkGame(testSettings("spy")).start()
            verifyAll {
                g.sides.white.init()
                g.sides.black.init()
                g.sides.white.startTurn()
            }
        }
    }

    @Nested
    inner class Stopping {
        private val results = drawBy(EndReason.DRAW_AGREEMENT)

        @Test
        fun `should disable running`() {
            val game = mkGame(normalSettings).start()

            game.stop(results)

            assertThat(game::running).isFalse()
        }

        @Test
        fun `should cancel coroutine scope`() {
            val game = mkGame(normalSettings).start()

            game.stop(results)

            assertThat(game.coroutineScope::isActive).isFalse()
        }

        @Test
        fun `should preserve results`() {
            val game = mkGame(normalSettings).start()

            game.stop(results)

            assertThat(game::results).isNotNull().isSameAs(results)
        }

        @Test
        fun `should throw when not started`() {
            val game = mkGame(normalSettings)

            assertThrows<IllegalStateException> {
                game.stop(results)
            }
        }

        @Test
        fun `should throw when already stopped`() {
            val game = mkGame(normalSettings).start()

            game.stop(results)

            assertThrows<IllegalStateException> {
                game.stop(results)
            }
        }
    }
}