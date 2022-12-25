package gregc.gregchess.bukkit

import gregc.gregchess.bukkit.match.BukkitChessEnvironment
import gregc.gregchess.bukkit.match.BukkitMatchInfo
import gregc.gregchess.bukkitutils.serialization.addBukkitSerializers
import gregc.gregchess.match.*
import gregc.gregchess.registry.RegistryKey
import gregc.gregchess.registry.StringKeySerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import java.util.*

internal object UUIDAsStringSerializer : KSerializer<UUID> {
    override val descriptor = PrimitiveSerialDescriptor("UUIDAsString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
}

@Suppress("UNCHECKED_CAST")
internal fun defaultModule() = SerializersModule {
    contextual(UUID::class, UUIDAsStringSerializer)
    contextual(ChessEnvironment::class, BukkitChessEnvironment.serializer() as KSerializer<ChessEnvironment>)
    contextual(MatchInfo::class, BukkitMatchInfo.serializer() as KSerializer<MatchInfo>)
    contextual(RegistryKey::class) { ser ->
        when(ser) {
            listOf(String.serializer()) -> StringKeySerializer(BukkitChessModule.modules)
            else -> throw UnsupportedOperationException()
        }
    }
    addBukkitSerializers()
}