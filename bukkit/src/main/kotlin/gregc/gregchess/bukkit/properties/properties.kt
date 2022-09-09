package gregc.gregchess.bukkit.properties

import gregc.gregchess.*
import gregc.gregchess.bukkit.config
import gregc.gregchess.bukkit.event.BukkitChessEventType
import gregc.gregchess.bukkit.registry.BukkitRegistry
import gregc.gregchess.bukkitutils.getPathString
import gregc.gregchess.event.ChessEvent
import gregc.gregchess.registry.*

class AddPropertiesEvent(
    private val playerProperties: MutableMap<PropertyType, PlayerProperty>,
    private val matchProperties: MutableMap<PropertyType, MatchProperty>
) : ChessEvent {
    fun player(id: PropertyType, f: (Color) -> String) {
        playerProperties[id] = object : PlayerProperty(id) {
            override fun invoke(s: Color) = f(s)
        }
    }

    fun match(id: PropertyType, f: () -> String) {
        matchProperties[id] = object : MatchProperty(id) {
            override fun invoke() = f()
        }
    }

    override val type get() = BukkitChessEventType.ADD_PROPERTIES
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

abstract class MatchProperty(val type: PropertyType) {
    abstract operator fun invoke(): String
}

val PropertyType.localName get() = module.config.getPathString("Scoreboard.${name.snakeToPascal()}")