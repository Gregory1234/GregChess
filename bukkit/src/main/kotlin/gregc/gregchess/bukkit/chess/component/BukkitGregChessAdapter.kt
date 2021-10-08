package gregc.gregchess.bukkit.chess.component

import gregc.gregchess.GregChessModule
import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.chess.AddPropertiesEvent
import gregc.gregchess.bukkit.chess.PropertyType
import gregc.gregchess.chess.ChessEventHandler
import gregc.gregchess.chess.ChessGame
import gregc.gregchess.chess.component.SimpleComponent
import gregc.gregchess.chess.component.TimeControl
import gregc.gregchess.chess.variant.ThreeChecks

class BukkitGregChessAdapter(game: ChessGame) : SimpleComponent(game) {

    companion object {
        @JvmField
        val TIME_REMAINING = GregChessModule.register("time_remaining", PropertyType())
        @JvmField
        val TIME_REMAINING_SIMPLE = GregChessModule.register("time_remaining_simple", PropertyType())
        @JvmField
        val CHECK_COUNTER = GregChessModule.register("check_counter", PropertyType())

        private val timeFormat: String get() = BukkitGregChessModule.config.getPathString("TimeFormat")
    }

    @ChessEventHandler
    fun addProperties(e: AddPropertiesEvent) {
        game.clock?.apply {
            if (timeControl.type == TimeControl.Type.FIXED) {
                e.game(TIME_REMAINING_SIMPLE) { timeRemaining(game.currentTurn).format(timeFormat) }
            } else {
                e.player(TIME_REMAINING) { timeRemaining(it).format(timeFormat) }
            }
        }
        game.getComponent<ThreeChecks.CheckCounter>()?.apply {
            e.player(CHECK_COUNTER) { this[it].toString() }
        }
    }
}