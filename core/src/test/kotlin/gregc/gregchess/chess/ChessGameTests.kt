package gregc.gregchess.chess

import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.mockk.*

private val basicSettings = testSettings("basic")
private val spyComponentSettings = testSettings("spy component", extra = listOf(TestComponentData))
private fun spyVariantSettings() = testSettings("spy variant", variant = spyk(TestVariant))

private fun mkGame(settings: GameSettings = basicSettings, players: BySides<String> = bySides("a", "b")) =
    ChessGame(settings, bySides {players[it].cpi})

fun componentExclude(c: TestComponent) {
    excludeRecords {
        c.validate()
    }
}

class ChessGameTests : FreeSpec({

    "ChessGame" - {
        "starting should" - {
            "make the game runring" {
                val g = mkGame().start()
                g.running.shouldBeTrue()
            }
            "start components" {
                val g = mkGame(spyComponentSettings).start()
                val c = g.getComponent<TestComponent>()!!
                verifySequence {
                    c.validate()
                    c.handleEvents(GameBaseEvent.START)
                    c.handleEvents(GameBaseEvent.RUNNING)
                    c.handleTurn(TurnEvent.START)
                }
            }
            "fail if" - {
                "already running" {
                    shouldThrowExactly<WrongStateException> {
                        mkGame().start().start()
                    }
                }
                "already stopped" {
                    shouldThrowExactly<WrongStateException> {
                        val g = mkGame().start()
                        val reason = whiteWonBy(TEST_END_REASON)
                        g.stop(reason)
                        g.start()
                    }
                }
            }
        }
        "stopping should" - {
            "save end reason" {
                val g = mkGame().start()
                val reason = whiteWonBy(TEST_END_REASON)
                g.stop(reason)
                g.results shouldBe reason
                g.running.shouldBeFalse()
            }
            "stop components" {
                val g = mkGame(spyComponentSettings).start()
                val c = g.getComponent<TestComponent>()!!
                clearRecords(c)
                g.stop(whiteWonBy(TEST_END_REASON))
                verifySequence {
                    c.handleEvents(GameBaseEvent.STOP)
                }
            }
            "fail if" - {
                "not running yet" {
                    shouldThrowExactly<WrongStateException> {
                        val g = mkGame()
                        val reason = whiteWonBy(TEST_END_REASON)
                        g.stop(reason)
                    }
                }
            }
        }
    }
})