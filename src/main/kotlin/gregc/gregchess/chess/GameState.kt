package gregc.gregchess.chess

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.lang.IllegalStateException
import java.time.LocalDateTime
import java.util.*
import kotlin.Exception

sealed class GameState(val started: Boolean, val stopped: Boolean, val running: Boolean) {
    sealed interface WithPlayers {
        val players: BySides<ChessPlayer>
        val white: ChessPlayer
        val black: ChessPlayer
    }
    sealed interface WithCurrentPlayer: WithPlayers {
        val currentTurn: ChessSide
    }
    sealed interface WithStartTime {
        val startTime: LocalDateTime
    }
    sealed interface WithSpectators {
        val spectatorUUIDs: List<UUID>
    }
    sealed interface WithEndReason {
        val endReason: ChessGame.EndReason
    }

    object Initial: GameState(false, false, false)
    data class Ready(
        override val players: BySides<ChessPlayer>
        ): GameState(false, false, false), WithPlayers {
        override val white = players.white
        override val black = players.black
        init {
            players.validate()
        }
    }
    data class Starting(
        override val players: BySides<ChessPlayer>,
        override val startTime: LocalDateTime = LocalDateTime.now(),
        override var currentTurn: ChessSide = ChessSide.WHITE
        ): GameState(false, false, false), WithCurrentPlayer, WithStartTime {
        constructor(ready: Ready) : this(ready.players)

        override val white = players.white
        override val black = players.black
        init {
            players.validate()
        }
    }
    data class Running(
        override val players: BySides<ChessPlayer>,
        override val startTime: LocalDateTime,
        override var currentTurn: ChessSide,
        override val spectatorUUIDs: MutableList<UUID> = mutableListOf()
    ): GameState(true, false, true), WithCurrentPlayer, WithStartTime, WithSpectators {
        constructor(starting: Starting) : this(starting.players, starting.startTime, starting.currentTurn)

        override val white = players.white
        override val black = players.black
        init {
            players.validate()
        }
    }
    data class Stopping(
        override val players: BySides<ChessPlayer>,
        override val startTime: LocalDateTime,
        override val currentTurn: ChessSide,
        override val spectatorUUIDs: MutableList<UUID> = mutableListOf(),
        override val endReason: ChessGame.EndReason
    ): GameState(true, false, false), WithCurrentPlayer, WithStartTime, WithSpectators, WithEndReason {
        constructor(running: Running, endReason: ChessGame.EndReason) :
                this(running.players, running.startTime, running.currentTurn, running.spectatorUUIDs, endReason)

        override val white = players.white
        override val black = players.black
        init {
            players.validate()
        }
    }
    data class Stopped(
        override val players: BySides<ChessPlayer>,
        override val startTime: LocalDateTime,
        override val currentTurn: ChessSide,
        override val endReason: ChessGame.EndReason
    ): GameState(true, true, false), WithCurrentPlayer, WithStartTime, WithEndReason {
        constructor(stopping: Stopping) :
                this(stopping.players, stopping.startTime, stopping.currentTurn, stopping.endReason)

        override val white = players.white
        override val black = players.black
        init {
            players.validate()
        }
    }
    data class Error(
        val state: GameState,
        val error: Exception
    ): GameState(false, true, false), WithEndReason {
        override val endReason: ChessGame.EndReason
            get() = ChessGame.EndReason.Error(error)
    }

}

val BySides<ChessPlayer>.bukkit get() = toList().filterIsInstance<BukkitChessPlayer>()
val BySides<ChessPlayer>.real get() = bukkit.map {it.player}.distinct()
inline fun BySides<ChessPlayer>.forEachReal(f: (Player) -> Unit) = real.forEach(f)
inline fun BySides<ChessPlayer>.forEachRealIndexed(s: ChessSide, f: (ChessSide, Player) -> Unit) =
    forEachUnique(s) { f(it.side, it.player) }
inline fun BySides<ChessPlayer>.forEachUnique(s: ChessSide, f: (BukkitChessPlayer) -> Unit) =
    real.mapNotNull { this[it, s] }.forEach(f)
fun BySides<ChessPlayer>.validate() = forEachIndexed { side, player ->
    if (player.side != side) throw IllegalStateException("Player's side wrong!")
}
operator fun BySides<ChessPlayer>.get(p: Player, s: ChessSide): BukkitChessPlayer? =
    bukkit.filter {it.player == p}.run { singleOrNull() ?: singleOrNull { it.side == s } }
operator fun BySides<ChessPlayer>.contains(p: Player) = p in real


inline fun GameState.WithPlayers.forEachPlayer(f: (ChessPlayer) -> Unit) = players.forEach(f)
inline fun GameState.WithPlayers.forEachReal(f: (Player) -> Unit) = players.forEachReal(f)
operator fun GameState.WithPlayers.get(s: ChessSide) = players[s]
operator fun GameState.WithPlayers.contains(p: Player) = p in players

val GameState.WithCurrentPlayer.currentPlayer: ChessPlayer get() = this[currentTurn]
operator fun GameState.WithCurrentPlayer.get(p: Player) = players[p, currentTurn]
inline fun GameState.WithCurrentPlayer.forEachUnique(f: (BukkitChessPlayer) -> Unit) =
    players.forEachUnique(currentTurn, f)
inline fun GameState.WithCurrentPlayer.forEachRealIndexed(f: (ChessSide, Player) -> Unit) =
    players.forEachRealIndexed(currentTurn, f)

val GameState.WithSpectators.spectators: List<Player>
    get() = spectatorUUIDs.mapNotNull { Bukkit.getPlayer(it) }
inline fun GameState.WithSpectators.forEachSpectator(f: (Player) -> Unit) = spectators.forEach(f)

class WrongStateException(s: GameState, cls: Class<*>): Exception("expected ${cls.name}, got $s")