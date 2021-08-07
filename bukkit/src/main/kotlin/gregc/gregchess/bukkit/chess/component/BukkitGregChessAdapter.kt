package gregc.gregchess.bukkit.chess.component

import gregc.gregchess.GregChessModule
import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.chess.AddPropertiesEvent
import gregc.gregchess.bukkit.chess.PropertyType
import gregc.gregchess.chess.ChessEventHandler
import gregc.gregchess.chess.ChessGame
import gregc.gregchess.chess.component.ChessClock
import gregc.gregchess.chess.component.Component
import gregc.gregchess.chess.variant.ThreeChecks

class BukkitGregChessAdapter(private val game: ChessGame): Component {
    object Settings : Component.Settings<BukkitGregChessAdapter> {
        override fun getComponent(game: ChessGame) = BukkitGregChessAdapter(game)
    }

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
            if (game.settings.getComponent<ChessClock.Settings>()?.type == ChessClock.Type.FIXED) {
                e.game(TIME_REMAINING_SIMPLE) { getTimeRemaining(game.currentTurn).format(timeFormat) ?: timeFormat }
            } else {
                e.player(TIME_REMAINING) { getTimeRemaining(it).format(timeFormat) ?: timeFormat }
            }
        }
        game.getComponent<ThreeChecks.CheckCounter>()?.apply {
            e.player(CHECK_COUNTER) { this[it].toString() }
        }
    }
}