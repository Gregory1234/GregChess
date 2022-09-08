@file:OptIn(ExperimentalSerializationApi::class)

package gregc.gregchess.bukkitutils.serialization

import gregc.gregchess.bukkitutils.upperFirst
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule

@SerialInfo
@Target(AnnotationTarget.PROPERTY)
annotation class BukkitConfigLowercase

class BukkitConfigEncoder(override val serializersModule: SerializersModule, private val consumer: (Any?) -> Unit) : Encoder {
    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder = beginSection()
    override fun beginCollection(descriptor: SerialDescriptor, collectionSize: Int): CompositeEncoder =
        when (descriptor.kind) {
            StructureKind.LIST -> beginList()
            StructureKind.MAP -> beginMap()
            else -> throw IllegalArgumentException("Unknown collection descriptor kind ${descriptor.kind}")
        }

    private fun beginSection(): CompositeEncoder = BukkitConfigSectionEncoder(serializersModule, mutableMapOf(), consumer)
    private fun beginList(): CompositeEncoder = BukkitConfigListEncoder(serializersModule, mutableListOf(), consumer)
    private fun beginMap(): CompositeEncoder = BukkitConfigMapEncoder(serializersModule, mutableMapOf(), consumer)

    override fun encodeBoolean(value: Boolean) = consumer(value)

    override fun encodeByte(value: Byte) = consumer(value)
    override fun encodeShort(value: Short) = consumer(value)
    override fun encodeInt(value: Int) = consumer(value)
    override fun encodeLong(value: Long) = consumer(value)

    override fun encodeChar(value: Char) = consumer(value)

    override fun encodeDouble(value: Double) = consumer(value)
    override fun encodeFloat(value: Float) = consumer(value)

    override fun encodeString(value: String) = consumer(value)
    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) =
        encodeString(enumDescriptor.getElementName(index))

    override fun encodeInline(descriptor: SerialDescriptor): Encoder = this
    override fun encodeNull() = consumer(null)

}


abstract class AbstractBukkitConfigCompositeEncoder : CompositeEncoder {
    protected abstract fun encodeObjectElement(descriptor: SerialDescriptor, index: Int, value: Any)

    override fun encodeBooleanElement(descriptor: SerialDescriptor, index: Int, value: Boolean) =
        encodeObjectElement(descriptor, index, value)


    override fun encodeByteElement(descriptor: SerialDescriptor, index: Int, value: Byte) =
        encodeObjectElement(descriptor, index, value)

    override fun encodeShortElement(descriptor: SerialDescriptor, index: Int, value: Short) =
        encodeObjectElement(descriptor, index, value)

    override fun encodeIntElement(descriptor: SerialDescriptor, index: Int, value: Int) =
        encodeObjectElement(descriptor, index, value)

    override fun encodeLongElement(descriptor: SerialDescriptor, index: Int, value: Long) =
        encodeObjectElement(descriptor, index, value)


    override fun encodeCharElement(descriptor: SerialDescriptor, index: Int, value: Char) =
        encodeObjectElement(descriptor, index, value)


    override fun encodeDoubleElement(descriptor: SerialDescriptor, index: Int, value: Double) =
        encodeObjectElement(descriptor, index, value)

    override fun encodeFloatElement(descriptor: SerialDescriptor, index: Int, value: Float) =
        encodeObjectElement(descriptor, index, value)


    override fun encodeStringElement(descriptor: SerialDescriptor, index: Int, value: String) =
        encodeObjectElement(descriptor, index, value)


    override fun encodeInlineElement(descriptor: SerialDescriptor, index: Int): Encoder =
        BukkitConfigEncoder(serializersModule) {
            if (it != null)
                encodeObjectElement(descriptor, index, it)
        }


    override fun <T : Any> encodeNullableSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        serializer: SerializationStrategy<T>,
        value: T?
    ) {
        if (value != null)
            encodeSerializableElement(descriptor, index, serializer, value)
    }


    override fun <T> encodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        serializer: SerializationStrategy<T>,
        value: T
    ) = serializer.serialize(BukkitConfigEncoder(serializersModule) {
        if (it != null)
            encodeObjectElement(descriptor, index, it)
    }, value)
}

class BukkitConfigSectionEncoder(
    override val serializersModule: SerializersModule,
    private val result: MutableMap<String, Any>,
    private val consumer: (Map<String, Any>) -> Unit
) : AbstractBukkitConfigCompositeEncoder() {

    override fun endStructure(descriptor: SerialDescriptor) = consumer(result)

    override fun encodeObjectElement(descriptor: SerialDescriptor, index: Int, value: Any) {
        val name = if (descriptor.getElementAnnotations(index).filterIsInstance<BukkitConfigLowercase>().isNotEmpty())
            descriptor.getElementName(index)
        else
            descriptor.getElementName(index).upperFirst()
        result[name] = value
    }

}

class BukkitConfigListEncoder(
    override val serializersModule: SerializersModule,
    private val result: MutableList<Any>,
    private val consumer: (List<Any>) -> Unit
) : AbstractBukkitConfigCompositeEncoder() {

    override fun endStructure(descriptor: SerialDescriptor) = consumer(result)

    override fun encodeObjectElement(descriptor: SerialDescriptor, index: Int, value: Any) =
        result.add(index, value)

}

class BukkitConfigMapEncoder(
    override val serializersModule: SerializersModule,
    private val result: MutableMap<String, Any>,
    private val consumer: (Map<String, Any>) -> Unit
) : AbstractBukkitConfigCompositeEncoder() {

    override fun endStructure(descriptor: SerialDescriptor) = consumer(result)

    private lateinit var key: String

    override fun encodeObjectElement(descriptor: SerialDescriptor, index: Int, value: Any) {
        if (index % 2 == 0)
            key = value.toString()
        else
            result[key] = value
    }

}