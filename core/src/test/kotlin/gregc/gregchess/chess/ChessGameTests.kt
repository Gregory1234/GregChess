package gregc.gregchess.chess

import gregc.gregchess.Config
import io.mockk.spyk
import io.mockk.verify
import org.junit.jupiter.api.*
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChessGameTests {

    init {
        Config.initTest()
    }

    val basicSettings = testSettings("basic")

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
            verify {
                a.sendTitle(any(), any())
                b.sendTitle(any(), any())
                a.sendMessage(any())
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
    }

}