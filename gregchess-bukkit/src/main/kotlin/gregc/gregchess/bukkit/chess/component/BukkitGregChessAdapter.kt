package gregc.gregchess.bukkit.chess.component

import gregc.gregchess.bukkit.chess.AddPropertiesEvent
import gregc.gregchess.bukkit.chess.PropertyType
import gregc.gregchess.bukkit.config
import gregc.gregchess.bukkitutils.format
import gregc.gregchess.bukkitutils.getPathString
import gregc.gregchess.chess.ChessEventHandler
import gregc.gregchess.chess.ChessGame
import gregc.gregchess.chess.component.Component
import gregc.gregchess.chess.component.TimeControl
import gregc.gregchess.chess.variant.ThreeChecks
import gregc.gregchess.registry.Register
import kotlinx.serialization.Serializable

@Serializable
object BukkitGregChessAdapter : Component {
    @JvmField
    @Register
    val TIME_REMAINING = PropertyType()
    @JvmField
    @Register
    val TIME_REMAINING_SIMPLE = PropertyType()
    @JvmField
    @Register
    val CHECK_COUNTER = PropertyType()

    private val timeFormat: String get() = config.getPathString("TimeFormat")

    @ChessEventHandler
    fun addProperties(game: ChessGame, e: AddPropertiesEvent) {
        game.clock?.apply {
            if (timeControl.type == TimeControl.Type.FIXED) {
                e.game(TIME_REMAINING_SIMPLE) { timeRemaining[game.currentTurn].format(timeFormat) }
            } else {
                e.player(TIME_REMAINING) { timeRemaining[it].format(timeFormat) }
            }
        }
        game.getComponent<ThreeChecks.CheckCounter>()?.apply {
            e.player(CHECK_COUNTER) { this[it].toString() }
        }
    }
}