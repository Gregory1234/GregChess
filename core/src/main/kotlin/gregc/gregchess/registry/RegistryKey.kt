package gregc.gregchess.registry

import gregc.gregchess.ChessModule
import gregc.gregchess.GregChess
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

data class RegistryKey<K>(val module: ChessModule, val key: K) {
    constructor(namespace: String, key: K) : this(ChessModule[namespace], key)

    override fun toString() = "${module.namespace}:$key"
}

fun String.toKey(): RegistryKey<String> {
    val sections = split(":")
    return when (sections.size) {
        1 -> RegistryKey(GregChess, this)
        2 -> RegistryKey(sections[0], sections[1])
        else -> throw IllegalArgumentException("Bad registry key: $this")
    }
}

interface NameRegistered {
    val key: RegistryKey<String>
}

val NameRegistered.name get() = key.key
val NameRegistered.module get() = key.module

object StringKeySerializer : KSerializer<RegistryKey<String>> {
    override val descriptor: SerialDescriptor get() = PrimitiveSerialDescriptor("StringRegistryKey", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: RegistryKey<String>) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): RegistryKey<String> = decoder.decodeString().toKey()
}