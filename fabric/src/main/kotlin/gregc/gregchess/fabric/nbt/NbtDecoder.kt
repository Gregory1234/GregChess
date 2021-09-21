@file:OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)

package gregc.gregchess.fabric.nbt

import kotlinx.serialization.*
import kotlinx.serialization.builtins.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.modules.SerializersModule
import net.minecraft.nbt.*

interface NbtDecoder : Decoder {
    fun decodeByteArray(): ByteArray
    fun decodeIntArray(): IntArray
    fun decodeLongArray(): LongArray
    fun decodeNbtElement(): NbtElement = decodeNullableNbtElement()!!
    fun decodeNullableNbtElement(): NbtElement?
}

interface NbtCompositeDecoder : CompositeDecoder {
    fun decodeByteArrayElement(descriptor: SerialDescriptor, index: Int): ByteArray
    fun decodeIntArrayElement(descriptor: SerialDescriptor, index: Int): IntArray
    fun decodeLongArrayElement(descriptor: SerialDescriptor, index: Int): LongArray
    fun decodeNbtElementElement(descriptor: SerialDescriptor, index: Int): NbtElement =
        decodeNullableNbtElementElement(descriptor, index)!!

    fun decodeNullableNbtElementElement(descriptor: SerialDescriptor, index: Int): NbtElement?
}

private inline fun <reified T : NbtElement, R> nbtAsOr(
    el: NbtElement?, fromString: (String) -> R, normal: (T) -> R
): R =
    (el as? T)?.let { normal(it) } ?: fromString((el as NbtKey).asString())

class NbtElementDecoder(override val serializersModule: SerializersModule, private val nbtElement: NbtElement?) :
    NbtDecoder {

    override fun decodeNotNullMark(): Boolean = decodeNullableNbtElement() != null


    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder = when (descriptor.kind) {
        StructureKind.LIST -> beginList()
        StructureKind.MAP -> beginMap()
        StructureKind.CLASS -> beginCompound()
        StructureKind.OBJECT -> beginCompound()
        else -> throw IllegalArgumentException("Unknown structure descriptor kind ${descriptor.kind}")
    }

    private fun beginCompound() = NbtCompoundDecoder(serializersModule, nbtElement as NbtCompound)
    private fun beginList() = NbtListDecoder(serializersModule, nbtElement as NbtList)
    private fun beginMap() = NbtMapDecoder(serializersModule, nbtElement as NbtCompound)

    override fun decodeBoolean(): Boolean = nbtAsOr<NbtByte, Int>(nbtElement, { it.toInt() }, { it.intValue() }) != 0

    override fun decodeByte(): Byte = nbtAsOr<NbtByte, Byte>(nbtElement, { it.toByte() }, { it.byteValue() })
    override fun decodeShort(): Short = nbtAsOr<NbtShort, Short>(nbtElement, { it.toShort() }, { it.shortValue() })
    override fun decodeInt(): Int = nbtAsOr<NbtInt, Int>(nbtElement, { it.toInt() }, { it.intValue() })
    override fun decodeLong(): Long = nbtAsOr<NbtLong, Long>(nbtElement, { it.toLong() }, { it.longValue() })

    override fun decodeChar(): Char = nbtAsOr<NbtInt, Int>(nbtElement, { it.toInt() }, { it.intValue() }).toChar()

    override fun decodeDouble() = nbtAsOr<NbtDouble, Double>(nbtElement, { it.toDouble() }, { it.doubleValue() })
    override fun decodeFloat(): Float = nbtAsOr<NbtFloat, Float>(nbtElement, { it.toFloat() }, { it.floatValue() })

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int =
        enumDescriptor.getElementIndex(decodeString())

    override fun decodeString(): String = nbtAsOr<NbtString, String>(nbtElement, { it }, { it.asString() })


    override fun decodeInline(inlineDescriptor: SerialDescriptor): Decoder = this
    override fun decodeNull() = null

    override fun decodeByteArray(): ByteArray = (nbtElement as NbtByteArray).byteArray
    override fun decodeIntArray(): IntArray = (nbtElement as NbtIntArray).intArray
    override fun decodeLongArray(): LongArray = (nbtElement as NbtLongArray).longArray

    override fun decodeNullableNbtElement(): NbtElement? = nbtElement

    @Suppress("UNCHECKED_CAST")
    override fun <T> decodeSerializableValue(deserializer: DeserializationStrategy<T>) = when (deserializer) {
        ByteArraySerializer() -> decodeByteArray() as T
        IntArraySerializer() -> decodeIntArray() as T
        LongArraySerializer() -> decodeLongArray() as T
        else -> deserializer.deserialize(this)
    }
}

abstract class AbstractNbtCompositeDecoder : NbtCompositeDecoder {
    override fun decodeBooleanElement(descriptor: SerialDescriptor, index: Int): Boolean =
        nbtAsOr<NbtByte, Int>(decodeNbtElementElement(descriptor, index), { it.toInt() }, { it.intValue() }) != 0


    override fun decodeByteElement(descriptor: SerialDescriptor, index: Int): Byte =
        nbtAsOr<NbtByte, Byte>(decodeNbtElementElement(descriptor, index), { it.toByte() }, { it.byteValue() })

    override fun decodeShortElement(descriptor: SerialDescriptor, index: Int): Short =
        nbtAsOr<NbtShort, Short>(decodeNbtElementElement(descriptor, index), { it.toShort() }, { it.shortValue() })

    override fun decodeIntElement(descriptor: SerialDescriptor, index: Int): Int =
        nbtAsOr<NbtInt, Int>(decodeNbtElementElement(descriptor, index), { it.toInt() }, { it.intValue() })

    override fun decodeLongElement(descriptor: SerialDescriptor, index: Int): Long =
        nbtAsOr<NbtLong, Long>(decodeNbtElementElement(descriptor, index), { it.toLong() }, { it.longValue() })


    override fun decodeCharElement(descriptor: SerialDescriptor, index: Int): Char =
        nbtAsOr<NbtInt, Int>(decodeNbtElementElement(descriptor, index), { it.toInt() }, { it.intValue() }).toChar()


    override fun decodeDoubleElement(descriptor: SerialDescriptor, index: Int): Double =
        nbtAsOr<NbtDouble, Double>(decodeNbtElementElement(descriptor, index), { it.toDouble() }, { it.doubleValue() })

    override fun decodeFloatElement(descriptor: SerialDescriptor, index: Int): Float =
        nbtAsOr<NbtFloat, Float>(decodeNbtElementElement(descriptor, index), { it.toFloat() }, { it.floatValue() })


    override fun decodeStringElement(descriptor: SerialDescriptor, index: Int): String =
        nbtAsOr<NbtString, String>(decodeNbtElementElement(descriptor, index), { it }, { it.asString() })


    override fun decodeInlineElement(descriptor: SerialDescriptor, index: Int): Decoder =
        NbtElementDecoder(serializersModule, decodeNbtElementElement(descriptor, index))


    override fun <T : Any> decodeNullableSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T?>,
        previousValue: T?
    ): T? {
        val el = decodeNullableNbtElementElement(descriptor, index)
        return if (el != null) deserializer.deserialize(NbtElementDecoder(serializersModule, el)) else null
    }


    @Suppress("UNCHECKED_CAST")
    override fun <T> decodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T>,
        previousValue: T?
    ): T = when (deserializer) {
        ByteArraySerializer() -> decodeByteArrayElement(descriptor, index) as T
        IntArraySerializer() -> decodeIntArrayElement(descriptor, index) as T
        LongArraySerializer() -> decodeLongArrayElement(descriptor, index) as T
        else ->
            deserializer.deserialize(NbtElementDecoder(serializersModule, decodeNbtElementElement(descriptor, index)))
    }


    override fun decodeByteArrayElement(descriptor: SerialDescriptor, index: Int): ByteArray =
        (decodeNbtElementElement(descriptor, index) as NbtByteArray).byteArray

    override fun decodeIntArrayElement(descriptor: SerialDescriptor, index: Int): IntArray =
        (decodeNbtElementElement(descriptor, index) as NbtIntArray).intArray

    override fun decodeLongArrayElement(descriptor: SerialDescriptor, index: Int): LongArray =
        (decodeNbtElementElement(descriptor, index) as NbtLongArray).longArray

    override fun endStructure(descriptor: SerialDescriptor) {
    }

    override fun decodeSequentially(): Boolean = true
}

class NbtCompoundDecoder(override val serializersModule: SerializersModule, private val nbtElement: NbtCompound) :
    AbstractNbtCompositeDecoder() {
    private var index = 0

    override fun decodeNullableNbtElementElement(descriptor: SerialDescriptor, index: Int): NbtElement? =
        nbtElement.get(descriptor.getElementName(index))

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        while (index < descriptor.elementsCount) {
            val name = descriptor.getElementName(index++)
            if (nbtElement.contains(name)) {
                return index - 1
            }
        }
        return CompositeDecoder.DECODE_DONE
    }
}

class NbtListDecoder(override val serializersModule: SerializersModule, private val nbtElement: NbtList) :
    AbstractNbtCompositeDecoder() {
    private var index = 0

    override fun decodeNullableNbtElementElement(descriptor: SerialDescriptor, index: Int): NbtElement? =
        nbtElement[index]

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int =
        if (++index > nbtElement.size) CompositeDecoder.DECODE_DONE else index - 1
}

private class NbtKey(val string: String) : NbtElement by NbtString.of(string) {
    override fun toString(): String = asString()
    override fun asString(): String = string
}

class NbtMapDecoder(override val serializersModule: SerializersModule, private val nbtElement: NbtCompound) :
    AbstractNbtCompositeDecoder() {

    private val keys = nbtElement.keys.toList()
    private val values = keys.map { nbtElement[it] }
    private var index = 0

    override fun decodeNullableNbtElementElement(descriptor: SerialDescriptor, index: Int): NbtElement? =
        if (index % 2 == 0) NbtKey(keys[index / 2]) else values[(index - 1) / 2]

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int =
        if (++index > 2 * values.size) CompositeDecoder.DECODE_DONE else index - 1
}
