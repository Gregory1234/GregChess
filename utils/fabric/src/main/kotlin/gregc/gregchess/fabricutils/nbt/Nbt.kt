package gregc.gregchess.fabricutils.nbt

import kotlinx.serialization.*
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import net.minecraft.nbt.NbtElement

sealed interface NbtFormat : SerialFormat {
    fun <T> encodeToNbtElement(serializer: SerializationStrategy<T>, value: T): NbtElement?
    fun <T> decodeFromNbtElement(deserializer: DeserializationStrategy<T>, nbt: NbtElement?): T
}

class Nbt(
    override val serializersModule: SerializersModule = EmptySerializersModule()
) : NbtFormat {

    override fun <T> encodeToNbtElement(serializer: SerializationStrategy<T>, value: T): NbtElement? {
        var result: NbtElement? = null
        NbtElementEncoder(serializersModule) { result = it }.encodeSerializableValue(serializer, value)
        return result
    }

    override fun <T> decodeFromNbtElement(deserializer: DeserializationStrategy<T>, nbt: NbtElement?): T {
        return NbtElementDecoder(serializersModule, nbt).decodeSerializableValue(deserializer)
    }
}

inline fun <reified T> Nbt.encodeToNbtElement(value: T) = encodeToNbtElement(serializersModule.serializer(), value)
inline fun <reified T> Nbt.decodeFromNbtElement(nbt: NbtElement?): T =
    decodeFromNbtElement(serializersModule.serializer(), nbt)