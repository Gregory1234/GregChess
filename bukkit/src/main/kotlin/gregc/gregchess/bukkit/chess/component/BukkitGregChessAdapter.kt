package gregc.gregchess.bukkit.chess.component

import gregc.gregchess.bukkit.BukkitRegistering
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
import kotlinx.serialization.Transient

@Serializable
class BukkitGregChessAdapter : Component {
    companion object : BukkitRegistering {
        @JvmField
        @Register
        val TIME_REMAINING = PropertyType()
        @JvmField
        @Register
        val TIME_REMAINING_SIMPLE = PropertyType()
        @JvmField
        @Register
        val CHECK_COUNTER = PropertyType()
    }

    override val type get() = BukkitComponentType.ADAPTER

    @Transient
    private lateinit var game: ChessGame

    override fun init(game: ChessGame) {
        this.game = game
    }

    private val timeFormat: String get() = config.getPathString("TimeFormat")

    @ChessEventHandler
    fun addProperties(e: AddPropertiesEvent) {
        game.clock?.apply {
            if (timeControl.type == TimeControl.Type.FIXED) {
                e.game(TIME_REMAINING_SIMPLE) { timeRemaining[game.currentTurn].format(timeFormat) }
            } else {
                e.player(TIME_REMAINING) { timeRemaining[it].format(timeFormat) }
            }
        }
        game[ThreeChecks.CHECK_COUNTER]?.apply {
            e.player(CHECK_COUNTER) { this[it].toString() }
        }
    }
}