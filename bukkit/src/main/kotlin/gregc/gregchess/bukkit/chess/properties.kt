package gregc.gregchess.bukkit.chess

import gregc.gregchess.bukkit.BukkitRegistryTypes
import gregc.gregchess.chess.ChessEvent
import gregc.gregchess.chess.Color
import gregc.gregchess.registry.NameRegistered

class AddPropertiesEvent(
    private val playerProperties: MutableMap<PropertyType, PlayerProperty>,
    private val gameProperties: MutableMap<PropertyType, GameProperty>
) : ChessEvent {
    fun player(id: PropertyType, f: (Color) -> String) {
        playerProperties[id] = object : PlayerProperty(id) {
            override fun invoke(s: Color) = f(s)
        }
    }

    fun game(id: PropertyType, f: () -> String) {
        gameProperties[id] = object : GameProperty(id) {
            override fun invoke() = f()
        }
    }
}

class PropertyException(property: PropertyType, color: Color? = null, override val cause: Throwable? = null)
    : RuntimeException("${color ?: ""} $property", cause)

class PropertyType : NameRegistered {
    override val key get() = BukkitRegistryTypes.PROPERTY_TYPE[this]

    override fun toString(): String = "$key@${hashCode().toString(16)}"
}

abstract class PlayerProperty(val type: PropertyType) {
    abstract operator fun invoke(s: Color): String
}

abstract class GameProperty(val type: PropertyType) {
    abstract operator fun invoke(): String
}