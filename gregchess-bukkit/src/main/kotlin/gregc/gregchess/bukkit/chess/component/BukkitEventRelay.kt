package gregc.gregchess.bukkit.chess.component

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Component
import gregc.gregchess.chess.player.ChessSide
import kotlinx.serialization.Serializable
import org.bukkit.Bukkit
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

@Serializable
object BukkitEventRelay : Component {

    @ChessEventHandler
    fun onStart(game: ChessGame, e: GameStartStageEvent) {
        if (e == GameStartStageEvent.BEGIN)
            Bukkit.getPluginManager().callEvent(GameStartEvent(game))
    }

    @ChessEventHandler
    fun onStop(game: ChessGame, e: GameStopStageEvent) {
        if (e == GameStopStageEvent.VERY_END || e == GameStopStageEvent.PANIC)
            Bukkit.getPluginManager().callEvent(GameEndEvent(game, e == GameStopStageEvent.VERY_END))
    }

    @ChessEventHandler
    fun onTurnEnd(game: ChessGame, e: TurnEvent) {
        if (e == TurnEvent.END)
            Bukkit.getPluginManager().callEvent(TurnEndEvent(game, game.currentSide))
    }

    override fun handleEvent(game: ChessGame, e: ChessEvent) {
        super.handleEvent(game, e)
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

class TurnEndEvent(val game: ChessGame, val player: ChessSide<*>) : Event() {
    override fun getHandlers() = handlerList

    companion object {
        @Suppress("unused")
        @JvmStatic
        fun getHandlerList(): HandlerList = handlerList
        private val handlerList = HandlerList()
    }
}


class GameEndEvent(val game: ChessGame, val normal: Boolean) : Event() {

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