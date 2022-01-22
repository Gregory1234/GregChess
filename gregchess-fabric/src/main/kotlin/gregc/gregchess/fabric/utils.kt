package gregc.gregchess.fabric

import gregc.gregchess.chess.ChessEnvironment
import gregc.gregchess.fabric.coroutines.FabricChessEnvironment
import gregc.gregchess.registry.RegistryKey
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.IntArraySerializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import net.minecraft.block.entity.BlockEntity
import net.minecraft.nbt.NbtHelper
import net.minecraft.nbt.NbtIntArray
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.*
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal const val MOD_ID = "gregchess"
internal const val MOD_NAME = "GregChess"

internal fun ident(name: String) = Identifier(MOD_ID, name)

class BlockEntityDirtyDelegate<T>(var value: T) : ReadWriteProperty<BlockEntity, T> {
    override operator fun getValue(thisRef: BlockEntity, property: KProperty<*>): T = value

    override operator fun setValue(thisRef: BlockEntity, property: KProperty<*>, value: T) {
        this.value = value
        thisRef.markDirty()
    }
}

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
}

fun String.toKey(): RegistryKey<String> {
    val sections = split(":")
    return when (sections.size) {
        1 -> RegistryKey(GregChess, this)
        2 -> RegistryKey(sections[0], sections[1])
        else -> throw IllegalArgumentException("Bad registry key: $this")
    }
}