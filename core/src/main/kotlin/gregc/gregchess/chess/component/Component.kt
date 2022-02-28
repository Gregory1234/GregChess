package gregc.gregchess.chess.component

import gregc.gregchess.*
import gregc.gregchess.chess.ChessGame
import gregc.gregchess.chess.ChessListener
import gregc.gregchess.registry.*
import kotlinx.serialization.*
import kotlinx.serialization.modules.SerializersModule
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.full.createType

@Serializable(with = ComponentType.Serializer::class)
class ComponentType<T : Component>(val cl: KClass<T>) : NameRegistered {
    object Serializer : NameRegisteredSerializer<ComponentType<*>>("ComponentType", Registry.COMPONENT_TYPE)

    override val key get() = Registry.COMPONENT_TYPE[this]

    override fun toString(): String = Registry.COMPONENT_TYPE.simpleElementToString(this)

    @RegisterAll(ComponentType::class)
    companion object {

        internal val AUTO_REGISTER = AutoRegisterType(ComponentType::class) { m, n, _ -> register(m, n); (cl.companionObjectInstance as? Registering)?.registerAll(m) }

        @JvmField
        val CHESSBOARD = ComponentType(Chessboard::class)
        @JvmField
        val CLOCK = ComponentType(ChessClock::class)

        fun registerCore(module: ChessModule) = AutoRegister(module, listOf(AUTO_REGISTER)).registerAll<ComponentType<*>>()
    }
}

@Serializable(with = ComponentSerializer::class)
interface Component : ChessListener {

    val type: ComponentType<out @SelfType Component>

    fun init(game: ChessGame) {}

}

@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
object ComponentSerializer : KeyRegisteredSerializer<ComponentType<*>, Component>("Component", ComponentType.Serializer) {

    @Suppress("UNCHECKED_CAST")
    override fun ComponentType<*>.valueSerializer(module: SerializersModule): KSerializer<Component> =
        module.serializer(cl.createType()) as KSerializer<Component>

    override val Component.key: ComponentType<*> get() = type

}

class ComponentNotFoundException(type: ComponentType<*>) : Exception(type.toString()) {
    constructor(cl: KClass<out Component>) : this(Registry.COMPONENT_TYPE.values.first { it.cl == cl })
}