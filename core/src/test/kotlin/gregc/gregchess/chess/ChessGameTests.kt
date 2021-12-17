package gregc.gregchess.chess

import assertk.assertThat
import assertk.assertions.*
import gregc.gregchess.chess.piece.PieceEvent
import io.mockk.*
import org.junit.jupiter.api.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChessGameTests {

    // TODO: move mocks to initialization to make tests faster
    // TODO: mock Chessboard

    private val playerA = "A"
    private val playerB = "B"

    private val normalSettings = testSettings("normal")

    private fun mkGame(settings: GameSettings) = ChessGame(TestChessEnvironment, settings, byColor(playerA, playerB))

    @BeforeAll
    fun setup() {
        setupRegistry()
    }

    @Nested
    inner class Constructing {
        @Test
        fun `should only construct and verify components`() {
            val cd = spyk(TestComponentData)
            val g = mkGame(testSettings("spy", extra = listOf(cd)))
            val c = g.getComponent<TestComponent>()!!
            verifySequence {
                cd.getComponent(g)
                c.validate()
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
            val g = mkGame(testSettings("spy", extra = listOf(TestComponentData))).start()
            val c = g.getComponent<TestComponent>()!!
            excludeRecords {
                c.handleEvent(match { it is PieceEvent })
            }
            verifySequence {
                c.validate()
                c.handleEvent(GameBaseEvent.START)
                c.handleEvent(GameBaseEvent.RUNNING)
                c.handleEvent(TurnEvent.START)
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