@file:OptIn(ExperimentalSerializationApi::class)

package gregc.gregchess.fabric.nbt

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import net.minecraft.nbt.*

interface NbtEncoder : Encoder {
    fun encodeByteArray(value: ByteArray)
    fun encodeIntArray(value: IntArray)
    fun encodeLongArray(value: LongArray)
    fun encodeNbtElement(value: NbtElement)
}

interface NbtCompositeEncoder : CompositeEncoder {
    fun encodeByteArrayElement(descriptor: SerialDescriptor, index: Int, value: ByteArray)
    fun encodeIntArrayElement(descriptor: SerialDescriptor, index: Int, value: IntArray)
    fun encodeLongArrayElement(descriptor: SerialDescriptor, index: Int, value: LongArray)
    fun encodeNbtElementElement(descriptor: SerialDescriptor, index: Int, value: NbtElement)
}

class NbtElementEncoder(
    override val serializersModule: SerializersModule,
    private val consumer: (NbtElement?) -> Unit
) : NbtEncoder {

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder = beginCompound()
    override fun beginCollection(descriptor: SerialDescriptor, collectionSize: Int): CompositeEncoder =
        when (descriptor.kind) {
            StructureKind.LIST -> beginList()
            StructureKind.MAP -> beginMap()
            else -> throw IllegalArgumentException("Unknown collection descriptor kind ${descriptor.kind}")
        }

    private fun beginCompound() = NbtCompoundEncoder(serializersModule, NbtCompound(), consumer)
    private fun beginList() = NbtListEncoder(serializersModule, NbtList(), consumer)
    private fun beginMap() = NbtMapEncoder(serializersModule, NbtCompound(), consumer)

    override fun encodeBoolean(value: Boolean) = consumer(NbtByte.of(value))

    override fun encodeByte(value: Byte) = consumer(NbtByte.of(value))
    override fun encodeShort(value: Short) = consumer(NbtShort.of(value))
    override fun encodeInt(value: Int) = consumer(NbtInt.of(value))
    override fun encodeLong(value: Long) = consumer(NbtLong.of(value))

    override fun encodeChar(value: Char) = consumer(NbtInt.of(value.code))

    override fun encodeDouble(value: Double) = consumer(NbtDouble.of(value))
    override fun encodeFloat(value: Float) = consumer(NbtFloat.of(value))

    override fun encodeString(value: String) = consumer(NbtString.of(value))
    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) =
        encodeString(enumDescriptor.getElementName(index))

    override fun encodeInline(inlineDescriptor: SerialDescriptor): Encoder = this
    override fun encodeNull() = consumer(null)

    override fun encodeByteArray(value: ByteArray) = consumer(NbtByteArray(value))
    override fun encodeIntArray(value: IntArray) = consumer(NbtIntArray(value))
    override fun encodeLongArray(value: LongArray) = consumer(NbtLongArray(value))

    override fun encodeNbtElement(value: NbtElement) = consumer(value)

    override fun <T> encodeSerializableValue(serializer: SerializationStrategy<T>, value: T) = when (serializer) {
        ByteArraySerializer() -> encodeByteArray(value as ByteArray)
        IntArraySerializer() -> encodeIntArray(value as IntArray)
        LongArraySerializer() -> encodeLongArray(value as LongArray)
        else -> serializer.serialize(this, value)
    }
}

abstract class AbstractNbtCompositeEncoder : NbtCompositeEncoder {
    override fun encodeBooleanElement(descriptor: SerialDescriptor, index: Int, value: Boolean) =
        encodeNbtElementElement(descriptor, index, NbtByte.of(value))


    override fun encodeByteElement(descriptor: SerialDescriptor, index: Int, value: Byte) =
        encodeNbtElementElement(descriptor, index, NbtByte.of(value))

    override fun encodeShortElement(descriptor: SerialDescriptor, index: Int, value: Short) =
        encodeNbtElementElement(descriptor, index, NbtShort.of(value))

    override fun encodeIntElement(descriptor: SerialDescriptor, index: Int, value: Int) =
        encodeNbtElementElement(descriptor, index, NbtInt.of(value))

    override fun encodeLongElement(descriptor: SerialDescriptor, index: Int, value: Long) =
        encodeNbtElementElement(descriptor, index, NbtLong.of(value))


    override fun encodeCharElement(descriptor: SerialDescriptor, index: Int, value: Char) =
        encodeNbtElementElement(descriptor, index, NbtInt.of(value.code))


    override fun encodeDoubleElement(descriptor: SerialDescriptor, index: Int, value: Double) =
        encodeNbtElementElement(descriptor, index, NbtDouble.of(value))

    override fun encodeFloatElement(descriptor: SerialDescriptor, index: Int, value: Float) =
        encodeNbtElementElement(descriptor, index, NbtFloat.of(value))


    override fun encodeStringElement(descriptor: SerialDescriptor, index: Int, value: String) =
        encodeNbtElementElement(descriptor, index, NbtString.of(value))


    override fun encodeInlineElement(descriptor: SerialDescriptor, index: Int): Encoder =
        NbtElementEncoder(serializersModule) { if (it != null) encodeNbtElementElement(descriptor, index, it) }


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
    ) = when (serializer) {
        ByteArraySerializer() -> encodeByteArrayElement(descriptor, index, value as ByteArray)
        IntArraySerializer() -> encodeIntArrayElement(descriptor, index, value as IntArray)
        LongArraySerializer() -> encodeLongArrayElement(descriptor, index, value as LongArray)
        else -> serializer.serialize(NbtElementEncoder(serializersModule) {
            if (it != null) encodeNbtElementElement(
                descriptor,
                index,
                it
            )
        }, value)
    }


    override fun encodeByteArrayElement(descriptor: SerialDescriptor, index: Int, value: ByteArray) =
        encodeNbtElementElement(descriptor, index, NbtByteArray(value))

    override fun encodeIntArrayElement(descriptor: SerialDescriptor, index: Int, value: IntArray) =
        encodeNbtElementElement(descriptor, index, NbtIntArray(value))

    override fun encodeLongArrayElement(descriptor: SerialDescriptor, index: Int, value: LongArray) =
        encodeNbtElementElement(descriptor, index, NbtLongArray(value))
}

class NbtCompoundEncoder(
    override val serializersModule: SerializersModule,
    private val result: NbtCompound,
    private val consumer: (NbtCompound) -> Unit
) : AbstractNbtCompositeEncoder() {

    override fun endStructure(descriptor: SerialDescriptor) = consumer(result)

    override fun encodeNbtElementElement(descriptor: SerialDescriptor, index: Int, value: NbtElement) {
        result.put(descriptor.getElementName(index), value)
    }

}

class NbtListEncoder(
    override val serializersModule: SerializersModule,
    private val result: NbtList,
    private val consumer: (NbtList) -> Unit
) : AbstractNbtCompositeEncoder() {

    override fun endStructure(descriptor: SerialDescriptor) = consumer(result)

    override fun encodeNbtElementElement(descriptor: SerialDescriptor, index: Int, value: NbtElement) =
        result.add(index, value)

}

class NbtMapEncoder(
    override val serializersModule: SerializersModule,
    private val result: NbtCompound,
    private val consumer: (NbtCompound) -> Unit
) : AbstractNbtCompositeEncoder() {

    override fun endStructure(descriptor: SerialDescriptor) = consumer(result)

    private lateinit var key: String

    override fun encodeNbtElementElement(descriptor: SerialDescriptor, index: Int, value: NbtElement) {
        if (index % 2 == 0)
            key = value.asString()
        else
            result.put(key, value)
    }

}