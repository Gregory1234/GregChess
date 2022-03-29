package gregc.gregchess.bukkit.chess.component

import gregc.gregchess.game.*
import gregc.gregchess.player.ChessSide
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.bukkit.Bukkit
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

@Serializable
class BukkitEventRelay : Component {

    override val type get() = BukkitComponentType.EVENT_RELAY

    @Transient
    private lateinit var game: ChessGame

    override fun init(game: ChessGame) {
        this.game = game
    }

    @ChessEventHandler
    fun onBaseEvent(e: GameBaseEvent) {
        if (e == GameBaseEvent.RUNNING || e == GameBaseEvent.SYNC)
            Bukkit.getPluginManager().callEvent(GameStartEvent(game))
        if (e == GameBaseEvent.CLEAR || e == GameBaseEvent.PANIC)
            Bukkit.getPluginManager().callEvent(GameEndEvent(game, e != GameBaseEvent.PANIC))
    }

    @ChessEventHandler
    fun onTurnEnd(e: TurnEvent) {
        if (e == TurnEvent.END)
            Bukkit.getPluginManager().callEvent(TurnEndEvent(game, game.currentSide))
    }

    override fun handleEvent(e: ChessEvent) {
        super.handleEvent(e)
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