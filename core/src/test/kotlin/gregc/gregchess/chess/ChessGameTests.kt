package gregc.gregchess.chess

import gregc.gregchess.Config
import gregc.gregchess.chess.variant.ChessVariant
import io.mockk.*
import org.junit.jupiter.api.*
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChessGameTests {

    init {
        Config.initTest()
        ChessVariant += spyk(TestVariant())
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
    ) = ChessGame(TestTimeManager(), Arena("arena"), settings).addPlayers {
        players.forEach { (h, s) ->
            human(h, s, false)
        }
    }

    fun mkGame(settings: GameSettings = basicSettings, players: BySides<HumanPlayer>) =
        ChessGame(TestTimeManager(), Arena("arena"), settings).addPlayers {
            players.forEachIndexed { s, h ->
                human(h, s, false)
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
            excludeRecords {
                a.name; b.name; a == any(); b == any()
            }
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
        fun `only gets extra components and fen from variant`() {
            val g = mkGame(spyVariantSettings)
            excludeRecords {
                g.variant.name
            }
            verifyAll {
                g.variant.extraComponents
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
            excludeRecords {
                a.name; b.name; a == any(); b == any()
            }
            verifyAll {
                a.sendTitle(any(), any())
                b.sendTitle(any(), any())
                a.sendMessage(any())
                b.sendMessage(any())
            }
        }

        @Test
        fun `starts components`() {
            val g = mkGame(spyComponentSettings).start()
            val c = g.getComponent<TestComponent>()!!
            verifySequence {
                c.addPlayer(humanA)
                c.addPlayer(humanB)
                c.init()
                c.start()
                c.begin()
                c.update()
                c.startTurn()
            }
        }

        @Test
        fun `throws when not initialized`() {
            assertThrows<WrongStateException> {
                ChessGame(TestTimeManager(), Arena("arena"), basicSettings).start()
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
                c.removePlayer(humanA)
                c.removePlayer(humanB)
                c.clear()
                c.veryEnd()
            }
        }
    }

}