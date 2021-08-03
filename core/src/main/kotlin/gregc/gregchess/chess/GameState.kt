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
        val results: GameResults<*>
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
        override val results: GameResults<*>
    ) : GameState(true, false, false), WithCurrentPlayer, WithStartTime, Ended {
        constructor(running: Running, results: GameResults<*>)
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
        override val results: GameResults<*>
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
        override val results: GameResults<*> = drawBy(EndReason.ERROR)
    }

}

val BySides<ChessPlayer>.humans get() = toList().filterIsInstance<HumanChessPlayer>()
val BySides<ChessPlayer>.real get() = humans.map { it.player }.distinct()
inline fun BySides<ChessPlayer>.forEachReal(f: (HumanPlayer) -> Unit) = real.forEach(f)
inline fun BySides<ChessPlayer>.forEachRealIndexed(s: Side, f: (Side, HumanPlayer) -> Unit) =
    forEachUnique(s) { f(it.side, it.player) }

inline fun BySides<ChessPlayer>.forEachUnique(s: Side, f: (HumanChessPlayer) -> Unit) =
    real.mapNotNull { this[it, s] }.forEach(f)

fun BySides<ChessPlayer>.validate() {
    for ((s, p) in toIndexedList())
        if (p.side != s)
            throw IllegalStateException("Player's side wrong!")
}

operator fun BySides<ChessPlayer>.get(p: HumanPlayer, s: Side): HumanChessPlayer? =
    humans.filter { it.player == p }.run { singleOrNull() ?: singleOrNull { it.side == s } }

operator fun BySides<ChessPlayer>.contains(p: HumanPlayer) = p in real


inline fun GameState.WithPlayers.forEachPlayer(f: (ChessPlayer) -> Unit) = players.forEach(f)
inline fun GameState.WithPlayers.forEachReal(f: (HumanPlayer) -> Unit) = players.forEachReal(f)
operator fun GameState.WithPlayers.get(s: Side) = players[s]
operator fun GameState.WithPlayers.contains(p: HumanPlayer) = p in players

val GameState.WithCurrentPlayer.currentPlayer: ChessPlayer get() = this[currentTurn]
val GameState.WithCurrentPlayer.currentOpponent: ChessPlayer get() = this[!currentTurn]
operator fun GameState.WithCurrentPlayer.get(p: HumanPlayer) = players[p, currentTurn]
inline fun GameState.WithCurrentPlayer.forEachUnique(f: (HumanChessPlayer) -> Unit) =
    players.forEachUnique(currentTurn, f)

inline fun GameState.WithCurrentPlayer.forEachRealIndexed(f: (Side, HumanPlayer) -> Unit) =
    players.forEachRealIndexed(currentTurn, f)

class WrongStateException(s: GameState, cls: Class<*>) : Exception("expected ${cls.name}, got $s")