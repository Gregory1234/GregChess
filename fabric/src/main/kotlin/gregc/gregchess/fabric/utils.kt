package gregc.gregchess.fabric

import com.mojang.authlib.GameProfile
import gregc.gregchess.chess.ChessEnvironment
import gregc.gregchess.fabric.coroutines.FabricChessEnvironment
import gregc.gregchess.registry.RegistryKey
import gregc.gregchess.registry.StringKeySerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.IntArraySerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.modules.SerializersModule
import net.minecraft.block.*
import net.minecraft.block.entity.BlockEntity
import net.minecraft.item.ItemStack
import net.minecraft.nbt.*
import net.minecraft.server.MinecraftServer
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraft.world.WorldAccess
import net.minecraft.world.event.GameEvent
import java.util.*
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.*

internal const val MOD_ID = "gregchess"
internal const val MOD_NAME = "GregChess"

internal fun ident(name: String) = Identifier(MOD_ID, name)

fun World.moveBlock(pos: BlockPos, drop: Boolean): Boolean {
    val blockState = getBlockState(pos)
    return if (blockState.isAir) {
        false
    } else {
        val fluidState = getFluidState(pos)
        if (blockState.block !is AbstractFireBlock) {
            this.syncWorldEvent(2001, pos, Block.getRawIdFromState(blockState))
        }
        if (drop) {
            val blockEntity = if (blockState.hasBlockEntity()) this.getBlockEntity(pos) else null
            Block.dropStacks(blockState, this, pos, blockEntity, null, ItemStack.EMPTY)
        }
        val bl = this.setBlockState(pos, fluidState.blockState, 67, 512)
        if (bl) {
            this.emitGameEvent(null, GameEvent.BLOCK_DESTROY, pos)
        }
        bl
    }
}

fun NbtCompound.ensureNotEmpty() = apply {
    if (isEmpty)
        putBoolean("empty", true)
}

class BlockEntityDirtyDelegate<T>(var value: T) : ReadWriteProperty<BlockEntity, T> {
    override operator fun getValue(thisRef: BlockEntity, property: KProperty<*>): T = value

    override operator fun setValue(thisRef: BlockEntity, property: KProperty<*>, value: T) {
        this.value = value
        if (thisRef.world?.isClient == false) {
            thisRef.markDirty()
            (thisRef.world as? ServerWorld)?.chunkManager?.markForUpdate(thisRef.pos)
        }
    }
}

class BlockReference<BE : BlockEntity>(val entityClass: KClass<BE>, private val posRef: () -> BlockPos?, private val worldRef: () -> WorldAccess?, private val posOffset: BlockPos = BlockPos.ORIGIN) {
    val pos get() = posRef()?.add(posOffset)
    val world get() = worldRef()
    val entity get() = pos?.let { world?.getBlockEntity(it) }?.let { entityClass.safeCast(it) }
    val state: BlockState get() = pos?.let { world?.getBlockState(it) } ?: Blocks.AIR.defaultState!!
    val block: Block get() = state.block
    fun offset(off: BlockPos) = BlockReference(entityClass, posRef, worldRef, posOffset.add(off))
    fun <T : BlockEntity> ofEntity(ent: KClass<T>) = BlockReference(ent, posRef, worldRef, posOffset)
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

fun String.toKey(): RegistryKey<String> {
    val sections = split(":")
    return when (sections.size) {
        1 -> RegistryKey(GregChess, this)
        2 -> RegistryKey(FabricChessModule[sections[0]], sections[1])
        else -> throw IllegalArgumentException("Bad registry key: $this")
    }
}