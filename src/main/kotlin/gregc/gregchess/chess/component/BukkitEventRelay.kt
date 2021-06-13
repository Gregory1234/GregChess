package gregc.gregchess.chess.component

import gregc.gregchess.chess.ChessGame
import gregc.gregchess.chess.ChessPlayer
import org.bukkit.Bukkit
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class BukkitEventRelay(private val game: ChessGame) : Component {
    object Settings : Component.Settings<BukkitEventRelay> {
        override fun getComponent(game: ChessGame) = BukkitEventRelay(game)
    }

    @GameEvent(GameBaseEvent.BEGIN)
    fun sendStartEvent() {
        Bukkit.getPluginManager().callEvent(GameStartEvent(game))
    }

    @GameEvent(GameBaseEvent.VERY_END, GameBaseEvent.PANIC, mod = TimeModifier.LATE)
    fun sendEndEvent() {
        Bukkit.getPluginManager().callEvent(GameEndEvent(game))
    }

    @GameEvent(GameBaseEvent.END_TURN)
    fun sendTurnEndEvent() {
        Bukkit.getPluginManager().callEvent(TurnEndEvent(game, game.currentPlayer))
    }

}

class GameStartEvent(val game: ChessGame) : Event() {

    override fun getHandlers() = handlerList

    companion object {
        @Suppress("unused")
        @JvmStatic
        fun getHandlerList(): HandlerList = handlerList
        private val handlerList = HandlerList()
    }
}

class TurnEndEvent(val game: ChessGame, val player: ChessPlayer) : Event() {
    override fun getHandlers() = handlerList

    companion object {
        @Suppress("unused")
        @JvmStatic
        fun getHandlerList(): HandlerList = handlerList
        private val handlerList = HandlerList()
    }
}


class GameEndEvent(val game: ChessGame) : Event() {

    override fun getHandlers() = handlerList

    companion object {
        @Suppress("unused")
        @JvmStatic
        fun getHandlerList(): HandlerList = handlerList
        private val handlerList = HandlerList()
    }
}
