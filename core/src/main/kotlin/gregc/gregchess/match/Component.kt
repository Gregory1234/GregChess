package gregc.gregchess.match

import gregc.gregchess.*
import gregc.gregchess.board.Chessboard
import gregc.gregchess.clock.ChessClock
import gregc.gregchess.registry.*
import kotlinx.serialization.*
import kotlinx.serialization.modules.SerializersModule
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.full.createType

fun interface ComponentIdentifier<T : Component> {
    fun matchesOrNull(c: Component): T?
}

@Serializable(with = ComponentType.Serializer::class)
class ComponentType<T : Component>(val cl: KClass<T>) : NameRegistered, ComponentIdentifier<T> {
    object Serializer : NameRegisteredSerializer<ComponentType<*>>("ComponentType", Registry.COMPONENT_TYPE)

    override val key get() = Registry.COMPONENT_TYPE[this]

    override fun toString(): String = Registry.COMPONENT_TYPE.simpleElementToString(this)

    @Suppress("UNCHECKED_CAST")
    override fun matchesOrNull(c: Component): T? = if (c.type == this) c as T else null

    @RegisterAll(ComponentType::class)
    companion object {

        internal val AUTO_REGISTER = AutoRegisterType(ComponentType::class) { m, n, _ ->
            Registry.COMPONENT_TYPE[m, n] = this
            (cl.objectInstance as? Registering)?.registerAll(m)
            (cl.companionObjectInstance as? Registering)?.registerAll(m)
        }

        @JvmField
        val CHESSBOARD = ComponentType(Chessboard::class)
        @JvmField
        val CLOCK = ComponentType(ChessClock::class)

        fun registerCore(module: ChessModule) = AutoRegister(module, listOf(AUTO_REGISTER)).registerAll<ComponentType<*>>()
    }
}

@Serializable(with = ComponentSerializer::class)
interface Component {

    val type: ComponentType<out @SelfType Component>

    fun init(match: ChessMatch, events: ChessEventRegistry) {}

}

object ComponentSerializer : KeyRegisteredSerializer<ComponentType<*>, Component>("Component", ComponentType.Serializer) {

    @Suppress("UNCHECKED_CAST")
    override fun ComponentType<*>.valueSerializer(module: SerializersModule): KSerializer<Component> =
        module.serializer(cl.createType()) as KSerializer<Component>

    override val Component.key: ComponentType<*> get() = type

}

class ComponentNotFoundException(type: ComponentIdentifier<*>) : Exception(type.toString())
