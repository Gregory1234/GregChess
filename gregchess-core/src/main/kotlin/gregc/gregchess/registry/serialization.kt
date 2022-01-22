package gregc.gregchess.registry

import gregc.gregchess.ClassMapSerializer
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.modules.SerializersModule
import kotlin.reflect.KClass

open class NameRegisteredSerializer<T : NameRegistered>(val name: String, val registryView: RegistryView<String, T>) :
    KSerializer<T> {

    final override val descriptor: SerialDescriptor get() = PrimitiveSerialDescriptor(name, PrimitiveKind.STRING)

    final override fun serialize(encoder: Encoder, value: T) {
        encoder.encodeString(value.key.toString())
    }

    final override fun deserialize(decoder: Decoder): T {
        return registryView[decoder.decodeString().toKey()]
    }

}

@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
@Suppress("UNCHECKED_CAST")
open class ClassRegisteredSerializer<T : Any>(
    val name: String,
    val registryType: BiRegistryView<String, KClass<out T>>
) : KSerializer<T> {
    final override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor(name) {
            element<String>("type")
            element("value", buildSerialDescriptor(name + "Value", SerialKind.CONTEXTUAL))
        }

    final override fun serialize(encoder: Encoder, value: T) {
        val cl = value::class
        val actualSerializer = cl.serializer()
        val id = registryType[cl].toString()
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, id)
            encodeSerializableElement(descriptor, 1, actualSerializer as KSerializer<T>, value)
        }
    }

    final override fun deserialize(decoder: Decoder): T = decoder.decodeStructure(descriptor) {
        var type: String? = null
        var ret: T? = null

        if (decodeSequentially()) { // sequential decoding protocol
            type = decodeStringElement(descriptor, 0)
            val serializer = registryType[type.toKey()].serializer()
            ret = decodeSerializableElement(descriptor, 1, serializer)
        } else {
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> type = decodeStringElement(descriptor, index)
                    1 -> {
                        val serializer = registryType[type!!.toKey()].serializer()
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

@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
abstract class KeyRegisteredSerializer<K: Any, T : Any>(
    val name: String,
    val keySerializer: KSerializer<K>
) : KSerializer<T> {

    abstract val T.key: K
    // TODO: add dependency on SerializersModule
    abstract val K.serializer: KSerializer<T>

    final override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor(name) {
            element("type", keySerializer.descriptor)
            element("value", buildSerialDescriptor(name + "Value", SerialKind.CONTEXTUAL))
        }

    final override fun serialize(encoder: Encoder, value: T) {
        val key = value.key
        encoder.encodeStructure(descriptor) {
            encodeSerializableElement(descriptor, 0, keySerializer, key)
            encodeSerializableElement(descriptor, 1, key.serializer, value)
        }
    }

    final override fun deserialize(decoder: Decoder): T = decoder.decodeStructure(descriptor) {
        var key: K? = null
        var ret: T? = null

        if (decodeSequentially()) { // sequential decoding protocol
            key = decodeSerializableElement(descriptor, 0, keySerializer)
            ret = decodeSerializableElement(descriptor, 1, key.serializer)
        } else {
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> key = decodeSerializableElement(descriptor, 0, keySerializer)
                    1 -> {
                        ret = decodeSerializableElement(descriptor, index, key!!.serializer)
                    }
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
        }
        ret!!
    }
}

open class KeyRegisteredListSerializer<K : Any, T : Any>(val base: KeyRegisteredSerializer<K, T>, name: String = base.name + "List") : ClassMapSerializer<Collection<T>, K, T>(name, base.keySerializer) {

    override fun Collection<T>.asMap(): Map<K, T> {
        val ret = associateBy { with(base) { it.key } }
        require(ret.size == size)
        return ret
    }

    override fun fromMap(m: Map<K, T>): Collection<T> {
        require(m.all { with(base) { it.value.key == it.key } })
        return m.values
    }

    @Suppress("UNCHECKED_CAST")
    override fun K.valueSerializer(module: SerializersModule) = with(base) { serializer }

}