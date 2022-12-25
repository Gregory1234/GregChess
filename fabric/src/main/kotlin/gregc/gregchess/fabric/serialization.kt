package gregc.gregchess.fabric

import com.mojang.authlib.GameProfile
import gregc.gregchess.fabric.match.FabricChessEnvironment
import gregc.gregchess.fabric.match.FabricMatchInfo
import gregc.gregchess.fabricutils.addFabricSerializers
import gregc.gregchess.match.ChessEnvironment
import gregc.gregchess.match.MatchInfo
import gregc.gregchess.registry.RegistryKey
import gregc.gregchess.registry.StringKeySerializer
import kotlinx.serialization.*
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.modules.SerializersModule
import net.minecraft.server.MinecraftServer
import java.util.*

@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
@PublishedApi
internal object GameProfileSerializer : KSerializer<GameProfile> {
    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("GameProfile") {
            element("id", buildSerialDescriptor("GameProfileUUID", SerialKind.CONTEXTUAL))
            element<String>("name")
        }

    override fun serialize(encoder: Encoder, value: GameProfile) = encoder.encodeStructure(descriptor) {
        encodeSerializableElement(descriptor, 0, encoder.serializersModule.getContextual(UUID::class)!!, value.id)
        encodeStringElement(descriptor, 1, value.name)
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder): GameProfile = decoder.decodeStructure(descriptor) {
        var id: UUID? = null
        var name: String? = null
        if (decodeSequentially()) { // sequential decoding protocol
            id = decodeSerializableElement(descriptor, 0, decoder.serializersModule.getContextual(UUID::class)!!)
            name = decodeStringElement(descriptor, 1)
        } else {
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> id = decodeSerializableElement(descriptor, index, decoder.serializersModule.getContextual(UUID::class)!!)
                    1 -> name = decodeStringElement(descriptor, index)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
        }
        GameProfile(id!!, name!!)
    }
}

@Suppress("UNCHECKED_CAST")
internal fun defaultModule(server: MinecraftServer): SerializersModule = SerializersModule {
    addFabricSerializers(server)
    contextual(ChessEnvironment::class, FabricChessEnvironment.serializer() as KSerializer<ChessEnvironment>)
    contextual(MatchInfo::class, FabricMatchInfo.serializer() as KSerializer<MatchInfo>)
    contextual(RegistryKey::class) { ser ->
        when(ser) {
            listOf(String.serializer()) -> StringKeySerializer(FabricChessModule.modules)
            else -> throw UnsupportedOperationException()
        }
    }
}