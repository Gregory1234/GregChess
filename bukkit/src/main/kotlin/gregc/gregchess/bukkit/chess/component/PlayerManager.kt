package gregc.gregchess.bukkit.chess.component

import gregc.gregchess.bukkit.*
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Component
import gregc.gregchess.interact
import gregc.gregchess.seconds

enum class GameStartStageEvent : ChessEvent {
    INIT, START, BEGIN
}

enum class GameStopStageEvent : ChessEvent {
    STOP, CLEAR, VERY_END, PANIC
}

enum class PlayerDirection {
    JOIN, LEAVE
}

data class HumanPlayerEvent(val human: HumanPlayer, val dir: PlayerDirection) : ChessEvent

class PlayerManager(private val game: ChessGame) : Component {
    object Settings : Component.Settings<PlayerManager> {
        override fun getComponent(game: ChessGame) = PlayerManager(game)
    }

    var quick: BySides<Boolean> = bySides(false)

    @ChessEventHandler
    fun handleEvents(e: GameBaseEvent) = with(game) {
        when (e) {
            GameBaseEvent.START -> {
                players.forEachReal {
                    callEvent(HumanPlayerEvent(it, PlayerDirection.JOIN))
                }
                callEvent(GameStartStageEvent.INIT)
                players.forEachUnique(currentTurn) { it.init() }
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
                players.forEachUnique(currentTurn) {
                    interact {
                        it.player.showGameResults(it.side, results)
                        if (!results.endReason.quick)
                            wait((if (quick[it.side]) 0 else 3).seconds)
                        callEvent(HumanPlayerEvent(it.player, PlayerDirection.LEAVE))
                    }
                }
                if (results.endReason.quick) {
                    callEvent(GameStopStageEvent.CLEAR)
                    players.forEach(ChessPlayer::stop)
                    finishStopping()
                    return
                }
                interact {
                    wait((if (quick.white && quick.black) 0 else 3).seconds)
                    waitTick()
                    callEvent(GameStopStageEvent.CLEAR)
                    waitTick()
                    players.forEach(ChessPlayer::stop)
                    finishStopping()
                }
            }
            GameBaseEvent.STOPPED -> {
                callEvent(GameStopStageEvent.VERY_END)
            }
            GameBaseEvent.PANIC -> {
                callEvent(GameStopStageEvent.PANIC)
            }
            else -> {
            }
        }
    }
}

val ChessGame.playerManager get() = requireComponent<PlayerManager>()

fun ChessGame.stop(results: GameResults<*>, quick: BySides<Boolean>) {
    playerManager.quick = quick
    stop(results)
}

fun ChessGame.quickStop(results: GameResults<*>) = stop(results, bySides(true))