package gregc.gregchess.chess

import java.time.LocalDateTime

sealed class GameState(val started: Boolean, val stopped: Boolean, val running: Boolean) {
    sealed interface WithPlayers {
        val players: BySides<ChessPlayer>
        val white: ChessPlayer
        val black: ChessPlayer
    }

    sealed interface WithCurrentPlayer : WithPlayers {
        val currentTurn: Side
    }

    sealed interface WithStartTime {
        val startTime: LocalDateTime
    }

    sealed interface Ended {
        val results: GameResults
    }

    object Initial : GameState(false, false, false)
    class Ready(
        override val players: BySides<ChessPlayer>
    ) : GameState(false, false, false), WithPlayers {
        override val white = players.white
        override val black = players.black

        init {
            players.validate()
        }
    }

    class Starting(
        override val players: BySides<ChessPlayer>,
        override val startTime: LocalDateTime = LocalDateTime.now(),
        override var currentTurn: Side = white
    ) : GameState(false, false, false), WithCurrentPlayer, WithStartTime {
        constructor(ready: Ready) : this(ready.players)

        override val white = players.white
        override val black = players.black

        init {
            players.validate()
        }
    }

    class Running(
        override val players: BySides<ChessPlayer>,
        override val startTime: LocalDateTime,
        override var currentTurn: Side
    ) : GameState(true, false, true), WithCurrentPlayer, WithStartTime {
        constructor(starting: Starting) : this(starting.players, starting.startTime, starting.currentTurn)

        override val white = players.white
        override val black = players.black

        init {
            players.validate()
        }
    }

    class Stopping(
        override val players: BySides<ChessPlayer>,
        override val startTime: LocalDateTime,
        override val currentTurn: Side,
        override val results: GameResults
    ) : GameState(true, false, false), WithCurrentPlayer, WithStartTime, Ended {
        constructor(running: Running, results: GameResults)
                : this(running.players, running.startTime, running.currentTurn, results)

        override val white = players.white
        override val black = players.black

        init {
            players.validate()
        }
    }

    class Stopped(
        override val players: BySides<ChessPlayer>,
        override val startTime: LocalDateTime,
        override val currentTurn: Side,
        override val results: GameResults
    ) : GameState(true, true, false), WithCurrentPlayer, WithStartTime, Ended {
        constructor(stopping: Stopping) :
                this(stopping.players, stopping.startTime, stopping.currentTurn, stopping.results)

        override val white = players.white
        override val black = players.black

        init {
            players.validate()
        }
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

operator fun GameState.WithPlayers.get(s: Side) = players[s]

val GameState.WithCurrentPlayer.currentPlayer: ChessPlayer get() = this[currentTurn]
val GameState.WithCurrentPlayer.currentOpponent: ChessPlayer get() = this[!currentTurn]

class WrongStateException(s: GameState, cls: Class<*>) : Exception("expected ${cls.name}, got $s")