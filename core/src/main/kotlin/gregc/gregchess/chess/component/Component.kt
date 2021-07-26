package gregc.gregchess.chess.component

import gregc.gregchess.chess.*
import kotlin.collections.set
import kotlin.reflect.KClass

interface Component {
    interface Settings<out T : Component> {
        fun getComponent(game: ChessGame): T
    }
}

class AddPropertiesEvent(
    private val playerPropertiesList: MutableMap<PropertyType<*>, PlayerProperty<*>>,
    private val gamePropertiesList: MutableMap<PropertyType<*>, GameProperty<*>>
): ChessEvent {
    val playerProperties get() = playerPropertiesList.toMap()
    val gameProperties get() = gamePropertiesList.toMap()

    fun <T> player(id: PropertyType<T>, f: (Side) -> T) {
        playerPropertiesList[id] = object : PlayerProperty<T>(id) {
            override fun invoke(s: Side): T = f(s)
        }
    }
    fun <T> game(id: PropertyType<T>, f: () -> T) {
        gamePropertiesList[id] = object : GameProperty<T>(id) {
            override fun invoke(): T = f()
        }
    }
}

class PropertyType<T>(val name: String) {
    override fun toString(): String = name
}

abstract class PlayerProperty<T>(val type: PropertyType<T>) {
    abstract operator fun invoke(s: Side): T
}

abstract class GameProperty<T>(val type: PropertyType<T>) {
    abstract operator fun invoke(): T
}

class ComponentNotFoundException(cl: KClass<out Component>) : Exception(cl.toString())
class ComponentSettingsNotFoundException(cl: KClass<out Component.Settings<*>>) : Exception(cl.toString())
class ComponentConfigNotFoundException(cl: KClass<out Component>) : Exception(cl.toString())

object ComponentConfig {
    private val values = mutableMapOf<KClass<out Component>, Any>()

    fun <T : Component> getAny(cl: KClass<T>) = values[cl]
    inline fun <reified T : Component> getAny() = getAny(T::class)
    fun <T : Component> requireAny(cl: KClass<T>) = getAny(cl) ?: throw ComponentConfigNotFoundException(cl)
    inline fun <reified T : Component> requireAny() = requireAny(T::class)
    inline fun <reified T : Component, reified R : Any> get() = getAny<T>() as? R
    inline fun <reified T : Component, reified R : Any> require() = requireAny<T>() as R
    operator fun set(cl: KClass<out Component>, v: Any) {
        values[cl] = v
    }
}