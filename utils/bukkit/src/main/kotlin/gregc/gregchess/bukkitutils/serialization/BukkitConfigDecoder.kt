@file:OptIn(ExperimentalSerializationApi::class)

package gregc.gregchess.bukkitutils.serialization

import gregc.gregchess.bukkitutils.upperFirst
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.modules.SerializersModule
import org.bukkit.configuration.ConfigurationSection

class BukkitConfigDecoder(override val serializersModule: SerializersModule, private val obj: Any?) : Decoder {

    override fun decodeNotNullMark(): Boolean = obj != null

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder = when (descriptor.kind) {
        StructureKind.LIST -> beginList()
        StructureKind.MAP -> beginMap()
        StructureKind.CLASS -> beginSection()
        StructureKind.OBJECT -> beginSection()
        else -> throw IllegalArgumentException("Unknown structure descriptor kind ${descriptor.kind}")
    }

    @Suppress("UNCHECKED_CAST")
    private fun beginSection(): CompositeDecoder = BukkitConfigSectionDecoder(serializersModule, (obj as? ConfigurationSection)?.getValues(false) ?: obj as Map<String, Any?>)

    private fun beginList(): CompositeDecoder = BukkitConfigListDecoder(serializersModule, obj as List<*>)

    @Suppress("UNCHECKED_CAST")
    private fun beginMap(): CompositeDecoder = BukkitConfigMapDecoder(serializersModule, (obj as? ConfigurationSection)?.getValues(false) ?: obj as Map<String, Any?>)

    override fun decodeBoolean(): Boolean = obj as Boolean

    override fun decodeByte(): Byte = (obj as Number).toByte()
    override fun decodeShort(): Short = (obj as Number).toShort()
    override fun decodeInt(): Int = (obj as Number).toInt()
    override fun decodeLong(): Long = (obj as Number).toLong()

    override fun decodeChar(): Char = decodeString().single()

    override fun decodeDouble(): Double = (obj as Number).toDouble()
    override fun decodeFloat(): Float = (obj as Number).toFloat()

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int =
        enumDescriptor.getElementIndex(decodeString())

    override fun decodeString(): String = obj.toString()

    override fun decodeInline(descriptor: SerialDescriptor): Decoder = this
    override fun decodeNull(): Nothing? = null
}

abstract class AbstractBukkitConfigCompositeDecoder : CompositeDecoder {

    protected abstract fun decodeObjectElement(descriptor: SerialDescriptor, index: Int): Any?

    override fun decodeBooleanElement(descriptor: SerialDescriptor, index: Int): Boolean =
        decodeObjectElement(descriptor, index) as Boolean

    override fun decodeByteElement(descriptor: SerialDescriptor, index: Int): Byte =
        (decodeObjectElement(descriptor, index) as Number).toByte()

    override fun decodeShortElement(descriptor: SerialDescriptor, index: Int): Short =
        (decodeObjectElement(descriptor, index) as Number).toShort()

    override fun decodeIntElement(descriptor: SerialDescriptor, index: Int): Int =
        (decodeObjectElement(descriptor, index) as Number).toInt()

    override fun decodeLongElement(descriptor: SerialDescriptor, index: Int): Long =
        (decodeObjectElement(descriptor, index) as Number).toLong()

    override fun decodeCharElement(descriptor: SerialDescriptor, index: Int): Char =
        decodeStringElement(descriptor, index).single()

    override fun decodeDoubleElement(descriptor: SerialDescriptor, index: Int): Double =
        (decodeObjectElement(descriptor, index) as Number).toDouble()

    override fun decodeFloatElement(descriptor: SerialDescriptor, index: Int): Float =
        (decodeObjectElement(descriptor, index) as Number).toFloat()

    override fun decodeStringElement(descriptor: SerialDescriptor, index: Int): String =
        decodeObjectElement(descriptor, index).toString()

    override fun decodeInlineElement(descriptor: SerialDescriptor, index: Int): Decoder =
        BukkitConfigDecoder(serializersModule, decodeObjectElement(descriptor, index))

    @ExperimentalSerializationApi
    override fun <T : Any> decodeNullableSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T?>,
        previousValue: T?
    ): T? {
        val obj = decodeObjectElement(descriptor, index)
        return if (obj != null) deserializer.deserialize(BukkitConfigDecoder(serializersModule, obj)) else null
    }

    override fun <T> decodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T>,
        previousValue: T?
    ): T = deserializer.deserialize(BukkitConfigDecoder(serializersModule, decodeObjectElement(descriptor, index)))

    override fun endStructure(descriptor: SerialDescriptor) {
    }

    abstract override fun decodeCollectionSize(descriptor: SerialDescriptor): Int
}

class BukkitConfigSectionDecoder(override val serializersModule: SerializersModule, private val map: Map<String, Any?>) : AbstractBukkitConfigCompositeDecoder() {
    private var index = 0

    private fun getElementName(descriptor: SerialDescriptor, index: Int) =
        if (descriptor.getElementAnnotations(index).filterIsInstance<BukkitConfigLowercase>().isNotEmpty())
            descriptor.getElementName(index)
        else
            descriptor.getElementName(index).upperFirst()

    override fun decodeObjectElement(descriptor: SerialDescriptor, index: Int): Any? =
        map[getElementName(descriptor, index)]

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        while (index < descriptor.elementsCount) {
            if (getElementName(descriptor, index++) in map) {
                return index - 1
            }
        }
        return CompositeDecoder.DECODE_DONE
    }

    override fun decodeCollectionSize(descriptor: SerialDescriptor): Int = descriptor.elementsCount
}

class BukkitConfigListDecoder(override val serializersModule: SerializersModule, private val list: List<Any?>) : AbstractBukkitConfigCompositeDecoder() {
    private var index = 0

    override fun decodeObjectElement(descriptor: SerialDescriptor, index: Int): Any? =
        list[index]

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int =
        if (++index > list.size) CompositeDecoder.DECODE_DONE else index - 1

    override fun decodeCollectionSize(descriptor: SerialDescriptor): Int = list.size

    override fun decodeSequentially(): Boolean = true
}

class BukkitConfigMapDecoder(override val serializersModule: SerializersModule, private val map: Map<String, Any?>) : AbstractBukkitConfigCompositeDecoder() {
    private val keys = map.keys.toList()
    private val values = keys.map { map[it] }
    private var index = 0

    override fun decodeObjectElement(descriptor: SerialDescriptor, index: Int): Any? =
        if (index % 2 == 0) keys[index / 2] else values[(index - 1) / 2]

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int =
        if (++index > 2 * values.size) CompositeDecoder.DECODE_DONE else index - 1

    override fun decodeCollectionSize(descriptor: SerialDescriptor): Int = values.size

    override fun decodeSequentially(): Boolean = true
}