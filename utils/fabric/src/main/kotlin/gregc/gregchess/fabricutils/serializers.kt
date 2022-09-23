package gregc.gregchess.fabricutils

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.IntArraySerializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.modules.SerializersModuleBuilder
import net.minecraft.nbt.NbtHelper
import net.minecraft.nbt.NbtIntArray
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.*

private object BlockPosAsLongSerializer : KSerializer<BlockPos> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("BlockPos", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: BlockPos) = encoder.encodeLong(value.asLong())

    override fun deserialize(decoder: Decoder): BlockPos = BlockPos.fromLong(decoder.decodeLong())

}

private object UUIDAsIntArraySerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor
        get() = IntArraySerializer().descriptor

    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeSerializableValue(IntArraySerializer(), NbtHelper.fromUuid(value).intArray)
    }

    override fun deserialize(decoder: Decoder): UUID =
        NbtHelper.toUuid(NbtIntArray(decoder.decodeSerializableValue(IntArraySerializer())))
}

fun SerializersModuleBuilder.addFabricSerializers(server: MinecraftServer) {
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
    contextual(MinecraftServer::class, object : KSerializer<MinecraftServer> {
        override val descriptor = buildClassSerialDescriptor("MinecraftServer") {}

        override fun serialize(encoder: Encoder, value: MinecraftServer) = encoder.encodeStructure(descriptor) {}

        override fun deserialize(decoder: Decoder): MinecraftServer = decoder.decodeStructure(descriptor) {
            require(decodeElementIndex(descriptor) == CompositeDecoder.DECODE_DONE)
            server
        }
    })
    contextual(UUID::class, UUIDAsIntArraySerializer)
    contextual(BlockPos::class, BlockPosAsLongSerializer)
}