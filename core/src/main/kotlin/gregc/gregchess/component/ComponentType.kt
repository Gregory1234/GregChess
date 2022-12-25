package gregc.gregchess.component

import gregc.gregchess.CoreRegistry
import gregc.gregchess.Registering
import gregc.gregchess.board.Chessboard
import gregc.gregchess.clock.ChessClock
import gregc.gregchess.event.ComplexEventListener
import gregc.gregchess.event.EventListener
import gregc.gregchess.match.ChessTimeManager
import gregc.gregchess.player.ChessSideManager
import gregc.gregchess.registry.*
import kotlinx.serialization.*
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObjectInstance

@Serializable(with = ComponentType.Serializer::class)
class ComponentType<T : Component> @PublishedApi internal constructor(val cl: KClass<T>, val serializer: KSerializer<T>)
    : NameRegistered, EventListener {
    @PublishedApi
    internal object Serializer : NameRegisteredSerializer<ComponentType<*>>("ComponentType", CoreRegistry.COMPONENT_TYPE)

    override val key get() = CoreRegistry.COMPONENT_TYPE[this]

    override fun toString(): String = CoreRegistry.COMPONENT_TYPE.simpleElementToString(this)

    override fun isOf(parent: EventListener): Boolean = parent == this

    override fun div(subOwner: Any): EventListener = ComplexEventListener(this, subOwner)

    @RegisterAll(ComponentType::class)
    companion object {

        inline operator fun <reified T : Component> invoke() = ComponentType(T::class, serializer())

        internal val AUTO_REGISTER = AutoRegisterType(ComponentType::class) { m, n, _ ->
            CoreRegistry.COMPONENT_TYPE[m, n] = this
            (cl.objectInstance as? Registering)?.registerAll(m)
            (cl.companionObjectInstance as? Registering)?.registerAll(m)
        }

        @JvmField
        val CHESSBOARD = ComponentType<Chessboard>()
        @JvmField
        val CLOCK = ComponentType<ChessClock>()
        @JvmField
        val SIDES = ComponentType<ChessSideManager>()
        @JvmField
        val TIME = ComponentType<ChessTimeManager>()

        fun registerCore(module: ChessModule) = AutoRegister(module, listOf(AUTO_REGISTER)).registerAll<ComponentType<*>>()
    }
}