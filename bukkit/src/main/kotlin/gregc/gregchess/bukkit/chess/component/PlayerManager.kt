package gregc.gregchess.bukkit.chess.component

import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.chess.*
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Component
import gregc.gregchess.chess.component.ComponentData
import gregc.gregchess.interact
import gregc.gregchess.seconds
import kotlinx.serialization.Serializable
import org.bukkit.entity.Player

enum class GameStartStageEvent : ChessEvent {
    INIT, START, BEGIN
}

enum class GameStopStageEvent : ChessEvent {
    STOP, CLEAR, VERY_END, PANIC
}

enum class PlayerDirection {
    JOIN, LEAVE
}

class PlayerEvent(val player: Player, val dir: PlayerDirection) : ChessEvent

@Serializable
object PlayerManagerData : ComponentData<PlayerManager> {
    override fun getComponent(game: ChessGame) = PlayerManager(game, this)
}

class PlayerManager(game: ChessGame, override val data: PlayerManagerData) : Component(game) {
    internal var quick: BySides<Boolean> = bySides(false)

    @ChessEventHandler
    fun handleEvents(e: GameBaseEvent) = with(game) {
        when (e) {
            GameBaseEvent.START -> {
                players.forEachReal {
                    callEvent(PlayerEvent(it, PlayerDirection.JOIN))
                    it.games += game
                    it.currentGame = game
                }
                callEvent(GameStartStageEvent.INIT)
                players.forEachUnique { it.init() }
                variant.start(game)
                callEvent(GameStartStageEvent.START)
            }
            GameBaseEvent.RUNNING -> {
                callEvent(GameStartStageEvent.BEGIN)
                runTaskTimer(0.seconds, 0.1.seconds) {
                    if (running)
                        update()
                    else
                        cancel()
                }
            }
            GameBaseEvent.STOP -> {
                val results = results!!
                callEvent(GameStopStageEvent.STOP)
                with(game.board) {
                    if (lastMove?.piece?.side == Side.WHITE) {
                        val wLast = lastMove
                        game.players.forEachReal { p ->
                            p.sendLastMoves(fullmoveCounter + 1u, wLast, null)
                        }
                    }
                }
                val pgn = PGN.generate(game)
                players.forEachUnique {
                    interact {
                        it.player.showGameResults(it.side, results)
                        if (!results.endReason.quick)
                            wait((if (quick[it.side]) 0 else 3).seconds)
                        callEvent(PlayerEvent(it.player, PlayerDirection.LEAVE))
                        it.player.sendPGN(pgn)
                        it.player.games -= game
                        it.player.currentGame = null
                    }
                }
                if (results.endReason.quick) {
                    callEvent(GameStopStageEvent.CLEAR)
                    players.forEach(ChessPlayer::stop)
                    callEvent(GameStopStageEvent.VERY_END)
                    return
                }
                interact {
                    wait((if (quick.white && quick.black) 0 else 3).seconds)
                    waitTick()
                    callEvent(GameStopStageEvent.CLEAR)
                    waitTick()
                    players.forEach(ChessPlayer::stop)
                    callEvent(GameStopStageEvent.VERY_END)
                }
            }
            GameBaseEvent.PANIC -> {
                callEvent(GameStopStageEvent.PANIC)
            }
            else -> {
            }
        }
    }

    @ChessEventHandler
    fun handleTurn(e: TurnEvent) {
        if (e == TurnEvent.END) {
            if (game.currentTurn == black) {
                with(game.board) {
                    val wLast = (if (moveHistory.size <= 1) null else moveHistory[moveHistory.size - 2])
                    val bLast = lastMove
                    game.players.forEachReal { p ->
                        p.sendLastMoves(game.board.fullmoveCounter, wLast, bLast)
                    }
                }
            }
        }
    }
}

val ChessGame.playerManager get() = requireComponent<PlayerManager>()

fun ChessGame.stop(results: GameResults, quick: BySides<Boolean>) {
    playerManager.quick = quick
    stop(results)
}

fun ChessGame.quickStop(results: GameResults) = stop(results, bySides(true))