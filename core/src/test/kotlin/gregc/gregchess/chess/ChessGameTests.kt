package gregc.gregchess.chess

import io.mockk.*
import org.junit.jupiter.api.*
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChessGameTests {

    init {
        val variant = spyk(TestVariant)
        variant.init()
        excludeRecords {
            variant.name
            variant.init()
        }
    }

    val basicSettings = testSettings("basic")
    val spyComponentSettings = testSettings("spy component", extra = listOf(TestComponent.Settings))
    val spyVariantSettings get() = testSettings("spy variant", variant = "test")

    val humanA = TestHuman("a")
    val humanB = TestHuman("b")
    val humanC = TestHuman("c")

    fun mkGame(
        settings: GameSettings = basicSettings,
        players: List<Pair<HumanPlayer, Side>> = listOf(humanA to Side.WHITE, humanB to Side.BLACK)
    ) = ChessGame(TestTimeManager(), settings).addPlayers {
        players.forEach { (h, s) ->
            human(h, s, false)
        }
    }

    fun mkGame(settings: GameSettings = basicSettings, players: BySides<HumanPlayer>) =
        ChessGame(TestTimeManager(), settings).addPlayers {
            players.forEachIndexed { s, h ->
                human(h, s, false)
            }
        }

    fun playerExclude(p: HumanPlayer) {
        excludeRecords {
            p.name
            @Suppress("UNUSED_EQUALS_EXPRESSION")
            p == any()
        }
    }

    @Nested
    inner class Initializing {
        @Test
        fun `works with 2 players`() {
            mkGame()
        }

        @Test
        fun `throws with 1 player`() {
            assertThrows<IllegalStateException> {
                mkGame(players = listOf(humanA to Side.WHITE))
            }
        }

        @Test
        fun `throws with duplicated sides`() {
            assertThrows<IllegalStateException> {
                mkGame(players = listOf(humanA to Side.WHITE, humanB to Side.WHITE, humanC to Side.BLACK))
            }
        }

        @Test
        fun `throws when already initialized`() {
            assertThrows<WrongStateException> {
                mkGame().addPlayers {
                    human(humanA, Side.WHITE, false)
                    human(humanB, Side.BLACK, false)
                }
            }
        }

        @Test
        fun `doesn't call players`() {
            val a = spyk(humanA)
            val b = spyk(humanB)
            mkGame(players = BySides(a, b))
            playerExclude(a)
            playerExclude(b)
            verify {
                a wasNot Called
                b wasNot Called
            }
        }

        @Test
        fun `doesn't call components`() {
            val g = mkGame(spyComponentSettings)
            val c = g.getComponent<TestComponent>()!!
            verify {
                c wasNot Called
            }
        }

        @Test
        fun `only gets required components and fen from variant`() {
            val g = mkGame(spyVariantSettings)
            verifyAll {
                g.variant.requiredComponents
                g.variant.genFEN(any())
            }
        }
    }

    @Nested
    inner class Starting {
        @Test
        fun `makes game running`() {
            val g = mkGame().start()
            assert(g.running)
        }

        @Test
        fun `sends messages to players`() {
            val a = spyk(humanA)
            val b = spyk(humanB)
            mkGame(players = BySides(a, b)).start()
            playerExclude(a)
            playerExclude(b)
            verifyAll {
                a.sendGameUpdate(Side.WHITE, listOf(GamePlayerStatus.START, GamePlayerStatus.TURN))
                b.sendGameUpdate(Side.BLACK, listOf(GamePlayerStatus.START))
            }
        }

        @Test
        fun `starts components`() {
            val g = mkGame(spyComponentSettings).start()
            val c = g.getComponent<TestComponent>()!!
            verifySequence {
                c.handlePlayer(HumanPlayerEvent(humanA, PlayerDirection.JOIN))
                c.handlePlayer(HumanPlayerEvent(humanB, PlayerDirection.JOIN))
                c.init()
                c.start()
                c.begin()
                c.update()
                c.handleTurn(TurnEvent.START)
            }
        }

        @Test
        fun `throws when not initialized`() {
            assertThrows<WrongStateException> {
                ChessGame(TestTimeManager(), basicSettings).start()
            }
        }

        @Test
        fun `throws when running`() {
            assertThrows<WrongStateException> {
                mkGame().start().start()
            }
        }

        @Test
        fun `throws when stopped`() {
            assertThrows<WrongStateException> {
                val g = mkGame().start()
                val reason = TestEndReason(Side.WHITE)
                g.stop(reason)
                g.start()
            }
        }

        @Test
        fun `starts variant`() {
            val g = mkGame(spyVariantSettings).start()
            verify {
                g.variant.start(g)
            }
        }
    }

    @Nested
    inner class Stopping {
        @Test
        fun `saves end reason`() {
            val g = mkGame().start()
            val reason = TestEndReason(Side.WHITE)
            g.stop(reason)
            assertEquals(reason, g.endReason)
            assert(!g.running)
        }

        @Test
        fun `throws when not running`() {
            assertThrows<WrongStateException> {
                val g = mkGame()
                val reason = TestEndReason(Side.WHITE)
                g.stop(reason)
            }
        }

        @Test
        fun `throws when not stopped`() {
            assertThrows<WrongStateException> {
                val g = mkGame().start()
                val reason = TestEndReason(Side.WHITE)
                g.stop(reason)
                g.stop(reason)
            }
        }

        @Test
        fun `stops components`() {
            val g = mkGame(spyComponentSettings).start()
            val c = g.getComponent<TestComponent>()!!
            clearRecords(c)
            g.stop(TestEndReason(Side.WHITE))
            verifySequence {
                c.stop()
                c.handlePlayer(HumanPlayerEvent(humanA, PlayerDirection.LEAVE))
                c.handlePlayer(HumanPlayerEvent(humanB, PlayerDirection.LEAVE))
                c.clear()
                c.veryEnd()
            }
        }
    }

}