package gregc.gregchess.chess

import gregc.gregchess.asIdent
import gregc.gregchess.chess.variant.ChessVariant
import io.mockk.*
import org.junit.jupiter.api.*
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChessGameTests {

    init {
        val variant = spyk(TestVariant)
        ChessVariant += variant
        excludeRecords {
            variant.id
        }
    }

    val basicSettings = testSettings("basic")
    val spyComponentSettings = testSettings("spy component", extra = listOf(TestComponent.Settings))
    val spyVariantSettings get() = testSettings("spy variant", variant = "test".asIdent())

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

    fun componentExclude(c: TestComponent) {
        excludeRecords {
            c.handleEvents(GameBaseEvent.PRE_INIT)
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
            componentExclude(c)
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
                c.handleEvents(GameBaseEvent.PRE_INIT)
                c.handlePlayer(HumanPlayerEvent(humanA, PlayerDirection.JOIN))
                c.handlePlayer(HumanPlayerEvent(humanB, PlayerDirection.JOIN))
                c.handleEvents(GameBaseEvent.INIT)
                c.handleEvents(GameBaseEvent.START)
                c.handleEvents(GameBaseEvent.BEGIN)
                c.handleEvents(GameBaseEvent.UPDATE)
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
                val reason = Side.WHITE.wonBy(TEST_END_REASON)
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
            val reason = Side.WHITE.wonBy(TEST_END_REASON)
            g.stop(reason)
            assertEquals(reason, g.results)
            assert(!g.running)
        }

        @Test
        fun `throws when not running`() {
            assertThrows<WrongStateException> {
                val g = mkGame()
                val reason = Side.WHITE.wonBy(TEST_END_REASON)
                g.stop(reason)
            }
        }

        @Test
        fun `throws when not stopped`() {
            assertThrows<WrongStateException> {
                val g = mkGame().start()
                val reason = Side.WHITE.wonBy(TEST_END_REASON)
                g.stop(reason)
                g.stop(reason)
            }
        }

        @Test
        fun `stops components`() {
            val g = mkGame(spyComponentSettings).start()
            val c = g.getComponent<TestComponent>()!!
            clearRecords(c)
            g.stop(Side.WHITE.wonBy(TEST_END_REASON))
            verifySequence {
                c.handleEvents(GameBaseEvent.STOP)
                c.handlePlayer(HumanPlayerEvent(humanA, PlayerDirection.LEAVE))
                c.handlePlayer(HumanPlayerEvent(humanB, PlayerDirection.LEAVE))
                c.handleEvents(GameBaseEvent.CLEAR)
                c.handleEvents(GameBaseEvent.VERY_END)
            }
        }
    }

}