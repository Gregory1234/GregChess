package gregc.gregchess.bukkit.chess

import gregc.gregchess.bukkit.BukkitRegistryTypes
import gregc.gregchess.chess.ChessEvent
import gregc.gregchess.chess.Side

class AddPropertiesEvent(
    private val playerProperties: MutableMap<PropertyType, PlayerProperty>,
    private val gameProperties: MutableMap<PropertyType, GameProperty>
) : ChessEvent {
    fun player(id: PropertyType, f: (Side) -> String) {
        playerProperties[id] = object : PlayerProperty(id) {
            override fun invoke(s: Side) = f(s)
        }
    }

    fun game(id: PropertyType, f: () -> String) {
        gameProperties[id] = object : GameProperty(id) {
            override fun invoke() = f()
        }
    }
}

class PropertyType {
    val module get() = BukkitRegistryTypes.PROPERTY_TYPE.getModule(this)
    val name get() = BukkitRegistryTypes.PROPERTY_TYPE[this]

    override fun toString(): String = "${module.namespace}:$name@${hashCode().toString(16)}"
}

abstract class PlayerProperty(val type: PropertyType) {
    abstract operator fun invoke(s: Side): String
}

abstract class GameProperty(val type: PropertyType) {
    abstract operator fun invoke(): String
}