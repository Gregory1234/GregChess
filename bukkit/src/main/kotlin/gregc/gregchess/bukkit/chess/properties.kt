package gregc.gregchess.bukkit.chess

import gregc.gregchess.bukkit.BukkitRegistry
import gregc.gregchess.game.ChessEvent
import gregc.gregchess.registry.NameRegistered
import gregc.gregchess.util.AutoRegisterType
import gregc.gregchess.util.Color

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
    override val key get() = BukkitRegistry.PROPERTY_TYPE[this]

    override fun toString(): String = BukkitRegistry.PROPERTY_TYPE.simpleElementToString(this)

    companion object {
        internal val AUTO_REGISTER = AutoRegisterType(PropertyType::class) { m, n, _ -> BukkitRegistry.PROPERTY_TYPE[m, n] = this }
    }
}

abstract class PlayerProperty(val type: PropertyType) {
    abstract operator fun invoke(s: Color): String
}

abstract class GameProperty(val type: PropertyType) {
    abstract operator fun invoke(): String
}