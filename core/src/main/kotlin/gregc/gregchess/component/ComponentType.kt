package gregc.gregchess.component

import gregc.gregchess.*
import gregc.gregchess.board.Chessboard
import gregc.gregchess.clock.ChessClock
import gregc.gregchess.event.ComplexEventListener
import gregc.gregchess.event.EventListener
import gregc.gregchess.player.ChessSideManager
import gregc.gregchess.registry.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObjectInstance

// TODO: add a more convenient function for construction
@Serializable(with = ComponentType.Serializer::class)
class ComponentType<T : Component>(val cl: KClass<T>, val serializer: KSerializer<T>) : NameRegistered, ComponentIdentifier<T>, EventListener {
    object Serializer : NameRegisteredSerializer<ComponentType<*>>("ComponentType", Registry.COMPONENT_TYPE)

    override val key get() = Registry.COMPONENT_TYPE[this]

    override fun toString(): String = Registry.COMPONENT_TYPE.simpleElementToString(this)

    override val matchedTypes: Set<ComponentType<out T>> get() = setOf(this)
    @Suppress("UNCHECKED_CAST")
    override fun matchesOrNull(c: Component): T? = if (c.type == this) c as T else null

    override fun isOf(parent: EventListener): Boolean = parent == this

    override fun div(subOwner: Any): EventListener = ComplexEventListener(this, subOwner)

    @RegisterAll(ComponentType::class)
    companion object {

        internal val AUTO_REGISTER = AutoRegisterType(ComponentType::class) { m, n, _ ->
            Registry.COMPONENT_TYPE[m, n] = this
            (cl.objectInstance as? Registering)?.registerAll(m)
            (cl.companionObjectInstance as? Registering)?.registerAll(m)
        }

        @JvmField
        val CHESSBOARD = ComponentType(Chessboard::class, Chessboard.serializer())
        @JvmField
        val CLOCK = ComponentType(ChessClock::class, ChessClock.serializer())
        @JvmField
        val SIDES = ComponentType(ChessSideManager::class, ChessSideManager.serializer())

        fun registerCore(module: ChessModule) = AutoRegister(module, listOf(AUTO_REGISTER)).registerAll<ComponentType<*>>()
    }
}