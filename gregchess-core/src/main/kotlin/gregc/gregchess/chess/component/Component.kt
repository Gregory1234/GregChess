package gregc.gregchess.chess.component

import gregc.gregchess.chess.*
import gregc.gregchess.registry.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

// TODO: remove this
interface ComponentData<out T : Component> {
    val componentClass: KClass<out T>
    fun getComponent(game: ChessGame): T
}

abstract class Component(protected val game: ChessGame) : ChessListener, ChessEventCaller {
    abstract val data: ComponentData<*>

    open fun validate() {}

    final override fun toString(): String = buildString {
        val cl = this@Component::class
        append(cl.componentKey)
        append("@")
        append(cl.hashCode())
        append("(game.uuid=")
        append(game.uuid)
        append(", data=")
        append(data)
        append(")")
    }

    final override fun callEvent(e: ChessEvent) = game.callEvent(e)
}

object ComponentDataMapSerializer :
    KeyRegisteredMapSerializer<ComponentData<*>>("ComponentDataMap", ChainRegistryView(Registry.COMPONENT_CLASS, Registry.COMPONENT_SERIALIZER))

val KClass<out Component>.componentDataClass get() = Registry.COMPONENT_DATA_CLASS[componentModule, this]
val KClass<out Component>.componentKey get() = Registry.COMPONENT_CLASS[this]
val KClass<out Component>.componentModule get() = componentKey.module
val KClass<out Component>.componentName get() = componentKey.key

class ComponentNotFoundException(cl: KClass<out Component>) : Exception(cl.toString())

@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
class SimpleComponentDataSerializer(private val componentClass: KClass<out SimpleComponent>)
    : KSerializer<SimpleComponentData<*>> {

    override val descriptor: SerialDescriptor
        get() = buildSerialDescriptor("SimpleComponentData", StructureKind.OBJECT)

    override fun serialize(encoder: Encoder, value: SimpleComponentData<*>) {
        encoder.beginStructure(descriptor).endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): SimpleComponentData<*> {
        decoder.beginStructure(descriptor).endStructure(descriptor)
        return SimpleComponentData(componentClass)
    }
}

class SimpleComponentData<T : SimpleComponent>(override val componentClass: KClass<out T>) : ComponentData<T> {
    override fun getComponent(game: ChessGame): T = componentClass.primaryConstructor!!.call(game)
}

open class SimpleComponent(game: ChessGame) : Component(game) {
    override val data: SimpleComponentData<*> get() = SimpleComponentData(this::class)
}