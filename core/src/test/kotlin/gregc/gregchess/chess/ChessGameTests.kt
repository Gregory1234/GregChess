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

private fun mkGame(
    settings: GameSettings = basicSettings,
    players: List<Pair<String, Side>> = listOf("a" to white, "b" to black)
) = ChessGame(settings).addPlayers {
    for ((n, s) in players)
        test(n, s)
}

private fun mkGame(settings: GameSettings = basicSettings, players: BySides<String>) =
    ChessGame(settings).addPlayers {
        for ((s, n) in players.toIndexedList())
            test(n, s)
    }

fun componentExclude(c: TestComponent) {
    excludeRecords {
        c.validate()
    }
}

class ChessGameTests : FreeSpec({

    "ChessGame" - {
        "initializing should" - {
            "succeed if" - {
                "provided 2 players on different sides" {
                    mkGame()
                }
            }
            "fail if" - {
                "provided 1 players" {
                    mkGame()
                }
                "provided 2 players on the same side" {
                    shouldThrowExactly<IllegalStateException> {
                        mkGame(players = listOf("a" to white, "b" to white, "c" to black))
                    }
                }
                "already initialized" {
                    shouldThrowExactly<WrongStateException> {
                        mkGame().addPlayers {
                            test("a", white)
                            test("b", black)
                        }
                    }
                }
            }
            "not call components" {
                val g = mkGame(spyComponentSettings)
                val c = g.getComponent<TestComponent>()!!
                componentExclude(c)
                verify {
                    c wasNot Called
                }
            }
            "only get required components, piece types and fen from variant" {
                val g = mkGame(spyVariantSettings())
                verifyAll {
                    g.variant.requiredComponents
                    g.variant.pieceTypes
                    g.variant.genFEN(any())
                }
            }
        }
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
                "not initialized yet" {
                    shouldThrowExactly<WrongStateException> {
                        ChessGame(basicSettings).start()
                    }
                }
                "already running" {
                    shouldThrowExactly<WrongStateException> {
                        mkGame().start().start()
                    }
                }
                "already stopped" {
                    shouldThrowExactly<WrongStateException> {
                        val g = mkGame().start()
                        val reason = white.wonBy(TEST_END_REASON)
                        g.stop(reason)
                        g.start()
                    }
                }
            }
        }
        "stopping should" - {
            "save end reason" {
                val g = mkGame().start()
                val reason = white.wonBy(TEST_END_REASON)
                g.stop(reason)
                g.results shouldBe reason
                g.running.shouldBeFalse()
            }
            "stop components" {
                val g = mkGame(spyComponentSettings).start()
                val c = g.getComponent<TestComponent>()!!
                clearRecords(c)
                g.stop(white.wonBy(TEST_END_REASON))
                g.finishStopping()
                verifySequence {
                    c.handleEvents(GameBaseEvent.STOP)
                    c.handleEvents(GameBaseEvent.STOPPED)
                }
            }
            "fail if" - {
                "not running yet" {
                    shouldThrowExactly<WrongStateException> {
                        val g = mkGame()
                        val reason = white.wonBy(TEST_END_REASON)
                        g.stop(reason)
                        g.finishStopping()
                    }
                }
                "already stopped" {
                    shouldThrowExactly<WrongStateException> {
                        val g = mkGame().start()
                        val reason = white.wonBy(TEST_END_REASON)
                        g.stop(reason)
                        g.finishStopping()
                        g.stop(reason)
                        g.finishStopping()
                    }
                }
            }
        }
    }
})