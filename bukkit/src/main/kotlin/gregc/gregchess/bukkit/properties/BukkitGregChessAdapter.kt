package gregc.gregchess.bukkit.properties

import gregc.gregchess.Register
import gregc.gregchess.bukkit.BukkitRegistering
import gregc.gregchess.bukkit.config
import gregc.gregchess.bukkit.match.BukkitComponentType
import gregc.gregchess.bukkitutils.format
import gregc.gregchess.bukkitutils.getPathString
import gregc.gregchess.clock.TimeControl
import gregc.gregchess.clock.clock
import gregc.gregchess.match.*
import gregc.gregchess.variant.ThreeChecks
import kotlinx.serialization.Serializable

@Serializable
object BukkitGregChessAdapter : Component, BukkitRegistering {
    @JvmField
    @Register
    val TIME_REMAINING = PropertyType()
    @JvmField
    @Register
    val TIME_REMAINING_SIMPLE = PropertyType()
    @JvmField
    @Register
    val CHECK_COUNTER = PropertyType()

    override val type get() = BukkitComponentType.ADAPTER

    private val timeFormat: String get() = config.getPathString("TimeFormat")

    @ChessEventHandler
    fun addProperties(match: ChessMatch, e: AddPropertiesEvent) {
        match.clock?.apply {
            if (timeControl.type == TimeControl.Type.FIXED) {
                e.match(TIME_REMAINING_SIMPLE) { timeRemaining[match.board.currentTurn].format(timeFormat) }
            } else {
                e.player(TIME_REMAINING) { timeRemaining[it].format(timeFormat) }
            }
        }
        match[ThreeChecks.CHECK_COUNTER]?.apply {
            e.player(CHECK_COUNTER) { this[it].toString() }
        }
    }
}