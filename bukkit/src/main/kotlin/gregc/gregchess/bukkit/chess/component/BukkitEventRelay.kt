package gregc.gregchess.bukkit.chess.component

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Component
import gregc.gregchess.chess.component.ComponentData
import kotlinx.serialization.Serializable
import org.bukkit.Bukkit
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

@Serializable
object BukkitEventRelayData : ComponentData<BukkitEventRelay> {
    override fun getComponent(game: ChessGame) = BukkitEventRelay(game, this)
}

class BukkitEventRelay(game: ChessGame, override val data: BukkitEventRelayData) : Component(game) {

    @ChessEventHandler
    fun onStart(e: GameStartStageEvent) {
        if (e == GameStartStageEvent.BEGIN)
            Bukkit.getPluginManager().callEvent(GameStartEvent(game))
    }

    @ChessEventHandler
    fun onStop(e: GameStopStageEvent) {
        if (e == GameStopStageEvent.VERY_END)
            Bukkit.getPluginManager().callEvent(GameEndEvent(game))
    }

    @ChessEventHandler
    fun onTurnEnd(e: TurnEvent) {
        if (e == TurnEvent.END)
            Bukkit.getPluginManager().callEvent(TurnEndEvent(game, game.currentPlayer))
    }

    @ChessEventHandler
    fun handleEvent(e: ChessEvent) {
        Bukkit.getPluginManager().callEvent(ChessGameEvent(game, e))
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

class ChessGameEvent(val game: ChessGame, val event: ChessEvent) : Event() {
    override fun getHandlers() = handlerList

    companion object {
        @Suppress("unused")
        @JvmStatic
        fun getHandlerList(): HandlerList = handlerList
        private val handlerList = HandlerList()
    }
}