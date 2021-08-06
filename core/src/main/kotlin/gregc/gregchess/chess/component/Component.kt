package gregc.gregchess.chess.component

import gregc.gregchess.chess.*
import gregc.gregchess.isValidName
import kotlin.collections.set
import kotlin.reflect.KClass

interface Component {
    interface Settings<out T : Component> {
        fun getComponent(game: ChessGame): T
    }
}

class AddPropertiesEvent(
    private val playerProperties: MutableMap<PropertyType<*>, PlayerProperty<*>>,
    private val gameProperties: MutableMap<PropertyType<*>, GameProperty<*>>
) : ChessEvent {
    fun <T> player(id: PropertyType<T>, f: (Side) -> T) {
        playerProperties[id] = object : PlayerProperty<T>(id) {
            override fun invoke(s: Side): T = f(s)
        }
    }

    fun <T> game(id: PropertyType<T>, f: () -> T) {
        gameProperties[id] = object : GameProperty<T>(id) {
            override fun invoke(): T = f()
        }
    }
}

class PropertyType<T>(val name: String) {
    init {
        require(name.isValidName())
    }

    override fun toString(): String = "$name@${hashCode().toString(16)}"
}

abstract class PlayerProperty<T>(val type: PropertyType<T>) {
    abstract operator fun invoke(s: Side): T
}

abstract class GameProperty<T>(val type: PropertyType<T>) {
    abstract operator fun invoke(): T
}

class ComponentNotFoundException(cl: KClass<out Component>) : Exception(cl.toString())
class ComponentSettingsNotFoundException(cl: KClass<out Component.Settings<*>>) : Exception(cl.toString())