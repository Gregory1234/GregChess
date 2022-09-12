package gregc.gregchess.registry

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.modules.SerializersModule

open class NameRegisteredSerializer<T : NameRegistered>(val name: String, val registryView: RegistryView<String, T>) :
    KSerializer<T> {

    final override val descriptor: SerialDescriptor get() = PrimitiveSerialDescriptor(name, PrimitiveKind.STRING)

    final override fun serialize(encoder: Encoder, value: T) {
        encoder.encodeSerializableValue(encoder.serializersModule.serializer(), value.key)
    }

    final override fun deserialize(decoder: Decoder): T {
        return registryView[decoder.decodeSerializableValue(decoder.serializersModule.serializer())]
    }

}

@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
abstract class KeyRegisteredSerializer<K: Any, T : Any>(
    val name: String,
    val keySerializer: KSerializer<K>
) : KSerializer<T> {

    abstract val T.key: K
    abstract fun K.valueSerializer(module: SerializersModule): KSerializer<T>

    final override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor(name) {
            element("type", keySerializer.descriptor)
            element("value", buildSerialDescriptor(name + "Value", SerialKind.CONTEXTUAL))
        }

    final override fun serialize(encoder: Encoder, value: T) {
        val key = value.key
        encoder.encodeStructure(descriptor) {
            encodeSerializableElement(descriptor, 0, keySerializer, key)
            encodeSerializableElement(descriptor, 1, key.valueSerializer(encoder.serializersModule), value)
        }
    }

    final override fun deserialize(decoder: Decoder): T = decoder.decodeStructure(descriptor) {
        var key: K? = null
        var ret: T? = null

        if (decodeSequentially()) { // sequential decoding protocol
            key = decodeSerializableElement(descriptor, 0, keySerializer)
            ret = decodeSerializableElement(descriptor, 1, key.valueSerializer(decoder.serializersModule))
        } else {
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> key = decodeSerializableElement(descriptor, 0, keySerializer)
                    1 -> {
                        ret = decodeSerializableElement(descriptor, index, key!!.valueSerializer(decoder.serializersModule))
                    }
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
        }
        ret!!
    }
}