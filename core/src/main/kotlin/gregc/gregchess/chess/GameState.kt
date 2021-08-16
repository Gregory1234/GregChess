package gregc.gregchess.chess

import java.time.LocalDateTime

sealed class GameState(val started: Boolean, val stopped: Boolean, val running: Boolean) {
    sealed interface WithStartTime {
        val startTime: LocalDateTime
    }

    sealed interface Ended {
        val results: GameResults
    }

    object Initial : GameState(false, false, false)

    class Running(
        override val startTime: LocalDateTime = LocalDateTime.now()
    ) : GameState(true, false, true), WithStartTime

    class Stopped(
        override val startTime: LocalDateTime,
        override val results: GameResults
    ) : GameState(true, false, false), WithStartTime, Ended {
        constructor(running: Running, results: GameResults) : this(running.startTime, results)
    }

    class Error(val state: GameState, val error: Exception) :
        GameState(false, true, false), Ended {
        override val results: GameResults = drawBy(EndReason.ERROR)
    }

}

fun BySides<ChessPlayer>.validate() {
    for ((s, p) in toIndexedList())
        if (p.side != s)
            throw IllegalStateException("Player's side wrong!")
}

class WrongStateException(s: GameState, cls: Class<*>) : Exception("expected ${cls.name}, got $s")