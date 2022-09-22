package gregc.gregchess.registry

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

data class RegistryKey<K>(val module: ChessModule, val key: K) {
    override fun toString() = "${module.namespace}:$key"
}

interface NameRegistered {
    val key: RegistryKey<String>
}

val NameRegistered.name get() = key.key
val NameRegistered.module get() = key.module

class StringKeySerializer(val modules: Set<ChessModule>) : KSerializer<RegistryKey<String>> {
    override val descriptor: SerialDescriptor get() = PrimitiveSerialDescriptor("StringRegistryKey", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: RegistryKey<String>) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): RegistryKey<String> {
        val sections = decoder.decodeString().split(":")
        return when(sections.size) {
            2 -> RegistryKey(modules.first { it.namespace == sections[0] }, sections[1])
            else -> throw IllegalArgumentException("Bad registry key: $this")
        }
    }
}