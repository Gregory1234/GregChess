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

open class KeyRegisteredMapSerializer<V>(name: String, val registryType: RegistryView<String, KSerializer<out V>>)
    : ClassMapSerializer<Map<RegistryKey<String>, V>, RegistryKey<String>, V>(name, StringKeySerializer) {
    final override fun Map<RegistryKey<String>, V>.asMap(): Map<RegistryKey<String>, V> = this
    final override fun fromMap(m: Map<RegistryKey<String>, V>): Map<RegistryKey<String>, V> = m
    @Suppress("UNCHECKED_CAST")
    final override fun RegistryKey<String>.valueSerializer(module: SerializersModule): KSerializer<V> =
        registryType[this] as KSerializer<V>
}