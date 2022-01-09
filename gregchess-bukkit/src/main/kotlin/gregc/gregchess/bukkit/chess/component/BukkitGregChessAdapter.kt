package gregc.gregchess.bukkit.chess.component

import gregc.gregchess.GregChess
import gregc.gregchess.bukkit.chess.AddPropertiesEvent
import gregc.gregchess.bukkit.chess.PropertyType
import gregc.gregchess.bukkit.config
import gregc.gregchess.bukkit.registerPropertyType
import gregc.gregchess.bukkitutils.format
import gregc.gregchess.bukkitutils.getPathString
import gregc.gregchess.chess.ChessEventHandler
import gregc.gregchess.chess.ChessGame
import gregc.gregchess.chess.component.SimpleComponent
import gregc.gregchess.chess.component.TimeControl
import gregc.gregchess.chess.variant.ThreeChecks

class BukkitGregChessAdapter(game: ChessGame) : SimpleComponent(game) {

    companion object {
        @JvmField
        val TIME_REMAINING = GregChess.registerPropertyType("time_remaining", PropertyType())
        @JvmField
        val TIME_REMAINING_SIMPLE = GregChess.registerPropertyType("time_remaining_simple", PropertyType())
        @JvmField
        val CHECK_COUNTER = GregChess.registerPropertyType("check_counter", PropertyType())

        private val timeFormat: String get() = config.getPathString("TimeFormat")
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