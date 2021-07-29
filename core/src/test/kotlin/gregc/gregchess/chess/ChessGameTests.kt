package gregc.gregchess.chess

import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.*

private val humanA = TestHuman("a")
private val humanB = TestHuman("b")
private val humanC = TestHuman("c")

private val basicSettings = testSettings("basic")
private val spyComponentSettings = testSettings("spy component", extra = listOf(TestComponent.Settings))
private fun spyVariantSettings() = testSettings("spy variant", variant = spyk(TestVariant))

private fun mkGame(
    settings: GameSettings = basicSettings,
    players: List<Pair<HumanPlayer, Side>> = listOf(humanA to white, humanB to black)
) = ChessGame(TestTimeManager(), settings).addPlayers {
    players.forEach { (h, s) ->
        human(h, s, false)
    }
}

private fun mkGame(settings: GameSettings = basicSettings, players: BySides<HumanPlayer>) =
    ChessGame(TestTimeManager(), settings).addPlayers {
        players.forEachIndexed { s, h ->
            human(h, s, false)
        }
    }

private fun playerExclude(p: HumanPlayer) {
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

class ChessGameTests: FreeSpec({

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
                        mkGame(players = listOf(humanA to white, humanB to white, humanC to black))
                    }
                }
                "already initialized" {
                    shouldThrowExactly<WrongStateException> {
                        mkGame().addPlayers {
                            human(humanA, white, false)
                            human(humanB, black, false)
                        }
                    }
                }
            }
            "not call players" {
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
            "not call components" {
                val g = mkGame(spyComponentSettings)
                val c = g.getComponent<TestComponent>()!!
                componentExclude(c)
                verify {
                    c wasNot Called
                }
            }
            "only get required components and fen from variant" {
                val g = mkGame(spyVariantSettings())
                verifyAll {
                    g.variant.requiredComponents
                    g.variant.genFEN(any())
                }
            }
        }
        "starting should" - {
            "make the game runring" {
                val g = mkGame().start()
                g.running.shouldBeTrue()
            }
            "send messages to players" {
                val a = spyk(humanA)
                val b = spyk(humanB)
                mkGame(players = BySides(a, b)).start()
                playerExclude(a)
                playerExclude(b)
                verifyAll {
                    a.sendGameUpdate(white, listOf(GamePlayerStatus.START, GamePlayerStatus.TURN))
                    b.sendGameUpdate(black, listOf(GamePlayerStatus.START))
                }
            }
            "start components" {
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
            "start variant" {
                val g = mkGame(spyVariantSettings()).start()
                verify {
                    g.variant.start(g)
                }
            }
            "fail if" - {
                "not initialized yet" {
                    val e = shouldThrowExactly<FailedToStartGameException> {
                        ChessGame(TestTimeManager(), basicSettings).start()
                    }
                    e.cause.shouldBeInstanceOf<WrongStateException>()
                }
                "already running" {
                    val e = shouldThrowExactly<FailedToStartGameException> {
                        mkGame().start().start()
                    }
                    e.cause.shouldBeInstanceOf<WrongStateException>()
                }
                "already stopped" {
                    val e = shouldThrowExactly<FailedToStartGameException> {
                        val g = mkGame().start()
                        val reason = white.wonBy(TEST_END_REASON)
                        g.stop(reason)
                        g.start()
                    }
                    e.cause.shouldBeInstanceOf<WrongStateException>()
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
                verifySequence {
                    c.handleEvents(GameBaseEvent.STOP)
                    c.handlePlayer(HumanPlayerEvent(humanA, PlayerDirection.LEAVE))
                    c.handlePlayer(HumanPlayerEvent(humanB, PlayerDirection.LEAVE))
                    c.handleEvents(GameBaseEvent.CLEAR)
                    c.handleEvents(GameBaseEvent.VERY_END)
                }
            }
            "fail if" - {
                "not running yet" {
                    shouldThrowExactly<WrongStateException> {
                        val g = mkGame()
                        val reason = white.wonBy(TEST_END_REASON)
                        g.stop(reason)
                    }
                }
                "already stopped" {
                    shouldThrowExactly<WrongStateException> {
                        val g = mkGame().start()
                        val reason = white.wonBy(TEST_END_REASON)
                        g.stop(reason)
                        g.stop(reason)
                    }
                }
            }
        }
    }
})