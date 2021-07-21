package gregc.gregchess.chess.component

import gregc.gregchess.chess.*
import org.bukkit.Bukkit
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class BukkitEventRelay(private val game: ChessGame) : Component {
    object Settings : Component.Settings<BukkitEventRelay> {
        override fun getComponent(game: ChessGame) = BukkitEventRelay(game)
    }

    @ChessEventHandler
    fun sendStartEvent(e: GameBaseEvent) {
        if (e == GameBaseEvent.BEGIN)
            Bukkit.getPluginManager().callEvent(GameStartEvent(game))
        if (e == GameBaseEvent.VERY_END)
            Bukkit.getPluginManager().callEvent(GameEndEvent(game))
    }

    @ChessEventHandler
    fun sendTurnEndEvent(e: TurnEvent) {
        if (e == TurnEvent.END)
            Bukkit.getPluginManager().callEvent(TurnEndEvent(game, game.currentPlayer))
    }

    @ChessEventHandler
    fun sendGeneralEvent(e: ChessEvent) {
        Bukkit.getPluginManager().callEvent(ChessGameEvent(game, e))
    }

}

data class GameStartEvent(val game: ChessGame) : Event() {

    override fun getHandlers() = handlerList

    companion object {
        @Suppress("unused")
        @JvmStatic
        fun getHandlerList(): HandlerList = handlerList
        private val handlerList = HandlerList()
    }
}

data class TurnEndEvent(val game: ChessGame, val player: ChessPlayer) : Event() {
    override fun getHandlers() = handlerList

    companion object {
        @Suppress("unused")
        @JvmStatic
        fun getHandlerList(): HandlerList = handlerList
        private val handlerList = HandlerList()
    }
}


data class GameEndEvent(val game: ChessGame) : Event() {

    override fun getHandlers() = handlerList

    companion object {
        @Suppress("unused")
        @JvmStatic
        fun getHandlerList(): HandlerList = handlerList
        private val handlerList = HandlerList()
    }
}

data class ChessGameEvent(val game: ChessGame, val event: ChessEvent): Event() {
    override fun getHandlers() = handlerList

    companion object {
        @Suppress("unused")
        @JvmStatic
        fun getHandlerList(): HandlerList = handlerList
        private val handlerList = HandlerList()
    }
}