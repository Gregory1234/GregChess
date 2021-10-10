package gregc.gregchess.chess.component

import gregc.gregchess.chess.*
import gregc.gregchess.registry.RegistryType
import gregc.gregchess.registry.toKey
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

@Serializable(with = ComponentDataSerializer::class)
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

@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
@Suppress("UNCHECKED_CAST")
object ComponentDataSerializer : KSerializer<ComponentData<*>> {
    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("ComponentData") {
            element<String>("type")
            element("value", buildSerialDescriptor("ComponentDataValue", SerialKind.CONTEXTUAL))
        }

    override fun serialize(encoder: Encoder, value: ComponentData<*>) {
        val cl = value.componentClass
        val actualSerializer = RegistryType.COMPONENT_SERIALIZER[cl.componentModule, cl]
        val id = cl.componentKey.toString()
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, id)
            encodeSerializableElement(descriptor, 1, actualSerializer as KSerializer<ComponentData<*>>, value)
        }
    }

    override fun deserialize(decoder: Decoder): ComponentData<*> = decoder.decodeStructure(descriptor) {
        var type: String? = null
        var ret: ComponentData<*>? = null

        if (decodeSequentially()) { // sequential decoding protocol
            type = decodeStringElement(descriptor, 0)
            val cl = RegistryType.COMPONENT_CLASS[type.toKey()]
            val serializer = RegistryType.COMPONENT_SERIALIZER[cl.componentModule, cl]
            ret = decodeSerializableElement(descriptor, 1, serializer)
        } else {
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> type = decodeStringElement(descriptor, index)
                    1 -> {
                        val cl = RegistryType.COMPONENT_CLASS[type!!.toKey()]
                        val serializer = RegistryType.COMPONENT_SERIALIZER[cl.componentModule, cl]
                        ret = decodeSerializableElement(descriptor, index, serializer)
                    }
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
        }
        ret!!
    }
}

val KClass<out Component>.componentDataClass get() = RegistryType.COMPONENT_DATA_CLASS[componentModule, this]
val KClass<out Component>.componentKey get() = RegistryType.COMPONENT_CLASS[this]
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