package gregc.gregchess.fabric

import com.mojang.authlib.GameProfile
import gregc.gregchess.fabric.coroutines.FabricChessEnvironment
import gregc.gregchess.match.ChessEnvironment
import gregc.gregchess.registry.RegistryKey
import gregc.gregchess.registry.StringKeySerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.IntArraySerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.modules.SerializersModule
import net.minecraft.nbt.NbtHelper
import net.minecraft.nbt.NbtIntArray
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.*


object BlockPosAsLongSerializer : KSerializer<BlockPos> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("BlockPos", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: BlockPos) = encoder.encodeLong(value.asLong())

    override fun deserialize(decoder: Decoder): BlockPos = BlockPos.fromLong(decoder.decodeLong())

}

object UUIDAsIntArraySerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor
        get() = IntArraySerializer().descriptor

    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeSerializableValue(IntArraySerializer(), NbtHelper.fromUuid(value).intArray)
    }

    override fun deserialize(decoder: Decoder): UUID =
        NbtHelper.toUuid(NbtIntArray(decoder.decodeSerializableValue(IntArraySerializer())))
}

object GameProfileSerializer : KSerializer<GameProfile> {
    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("GameProfile") {
            element("id", UUIDAsIntArraySerializer.descriptor)
            element<String>("name")
        }

    override fun serialize(encoder: Encoder, value: GameProfile) = encoder.encodeStructure(descriptor) {
        encodeSerializableElement(descriptor, 0, UUIDAsIntArraySerializer, value.id)
        encodeStringElement(descriptor, 1, value.name)
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder): GameProfile = decoder.decodeStructure(descriptor) {
        var id: UUID? = null
        var name: String? = null
        if (decodeSequentially()) { // sequential decoding protocol
            id = decodeSerializableElement(descriptor, 0, UUIDAsIntArraySerializer)
            name = decodeStringElement(descriptor, 1)
        } else {
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> id = decodeSerializableElement(descriptor, index, UUIDAsIntArraySerializer)
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
    contextual(World::class, object : KSerializer<World> {
        override val descriptor = PrimitiveSerialDescriptor("World", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: World) {
            encoder.encodeString(value.registryKey.value.toString())
        }

        override fun deserialize(decoder: Decoder): World {
            val id = Identifier(decoder.decodeString())
            return server.worlds.first { it.registryKey.value == id }
        }
    })
    contextual(UUID::class, UUIDAsIntArraySerializer)
    contextual(BlockPos::class, BlockPosAsLongSerializer)
    contextual(ChessEnvironment::class, FabricChessEnvironment.serializer() as KSerializer<ChessEnvironment>)
    contextual(RegistryKey::class) { ser ->
        when(ser) {
            listOf(String.serializer()) -> StringKeySerializer(FabricChessModule.modules)
            else -> throw UnsupportedOperationException()
        }
    }
}