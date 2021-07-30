package gregc.gregchess.bukkit.chess.component

import gregc.gregchess.bukkit.BukkitTimeManager
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Component
import gregc.gregchess.seconds

enum class GameStartStageEvent: ChessEvent {
    INIT, START, BEGIN
}

class PlayerManager(private val game: ChessGame) : Component {
    object Settings : Component.Settings<PlayerManager> {
        override fun getComponent(game: ChessGame) = PlayerManager(game)
    }

    @ChessEventHandler
    fun handleEvents(e: GameBaseEvent) = when(e){
        GameBaseEvent.START ->
        {
            game.players.forEachReal {
                game.components.callEvent(HumanPlayerEvent(it, PlayerDirection.JOIN))
            }
            game.components.callEvent(GameStartStageEvent.INIT)
            game.players.forEachUnique(game.currentTurn) { it.init() }
            game.variant.start(game)
            game.components.callEvent(GameStartStageEvent.START)
        }
        GameBaseEvent.RUNNING ->
        {
            game.components.callEvent(GameStartStageEvent.BEGIN)
            BukkitTimeManager.runTaskTimer(0.seconds, 0.1.seconds) {
                if (game.running)
                    game.update()
                else
                    cancel()
            }
        }
        else -> {}
    }
}