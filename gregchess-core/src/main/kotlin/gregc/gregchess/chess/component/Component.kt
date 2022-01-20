package gregc.gregchess.chess.component

import gregc.gregchess.*
import gregc.gregchess.chess.ChessGame
import gregc.gregchess.chess.ChessListener
import gregc.gregchess.registry.*
import kotlinx.serialization.*
import kotlinx.serialization.modules.SerializersModule
import kotlin.reflect.KClass
import kotlin.reflect.full.createType

@Serializable(with = ComponentType.Serializer::class)
class ComponentType<T : Component>(val cl: KClass<T>) : NameRegistered {
    object Serializer : NameRegisteredSerializer<ComponentType<*>>("ComponentType", Registry.COMPONENT_TYPE)

    override val key get() = Registry.COMPONENT_TYPE[this]

    override fun toString(): String = Registry.COMPONENT_TYPE.simpleElementToString(this)

    @RegisterAll(ComponentType::class)
    companion object {

        internal val AUTO_REGISTER = AutoRegisterType(ComponentType::class) { m, n, _ -> register(m, n) }

        @JvmField
        val CHESSBOARD = ComponentType(Chessboard::class)
        @JvmField
        val CLOCK = ComponentType(ChessClock::class)

        fun registerCore(module: ChessModule) = AutoRegister(module, listOf(AUTO_REGISTER)).registerAll<ComponentType<*>>()
    }
}

interface Component : ChessListener {

    val type: ComponentType<*>

    fun init(game: ChessGame) {}

}

object ComponentMapSerializer : ClassMapSerializer<Map<ComponentType<*>, Component>, ComponentType<*>, Component>("ComponentDataMap", ComponentType.Serializer) {

    override fun Map<ComponentType<*>, Component>.asMap() = this

    override fun fromMap(m: Map<ComponentType<*>, Component>) = m

    @Suppress("UNCHECKED_CAST")
    override fun ComponentType<*>.valueSerializer(module: SerializersModule) = module.serializer(cl.createType()) as KSerializer<Component>

}

class ComponentNotFoundException(type: ComponentType<*>) : Exception(type.toString()) {
    constructor(cl: KClass<out Component>) : this(Registry.COMPONENT_TYPE.values.first { it.cl == cl })
}