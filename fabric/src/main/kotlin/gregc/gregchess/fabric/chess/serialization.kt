@file:OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
@file:Suppress("UNCHECKED_CAST")

package gregc.gregchess.fabric.chess

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.ComponentDataSerializer
import gregc.gregchess.chess.variant.ChessVariant
import gregc.gregchess.fabric.UUIDAsIntArraySerializer
import gregc.gregchess.fabric.nbt.*
import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import net.minecraft.nbt.NbtCompound
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Identifier
import net.minecraft.world.World
import java.util.*

fun defaultModule(server: MinecraftServer): SerializersModule = SerializersModule {
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
}


// TODO: remove the repetition

fun ChessGame.serializeToNbt(config: Nbt): NbtCompound = NbtCompound().apply {
    put("uuid", config.encodeToNbtElement(uuid))
    put("players", config.encodeToNbtElement(BySides.serializer(ChessPlayerInfoSerializer), bySides { players[it].info }))
    put("preset", config.encodeToNbtElement(settings.name))
    put("variant", config.encodeToNbtElement(variant))
    put("simpleCastling", config.encodeToNbtElement(settings.simpleCastling))
    put("components", config.encodeToNbtElement(ListSerializer(ComponentDataSerializer), components.map { it.data }))
}

fun NbtCompound.recreateGameFromNbt(config: Nbt): ChessGame {
    val uuid: UUID = config.decodeFromNbtElement(get("uuid")!!)
    val players: BySides<ChessPlayerInfo> = config.decodeFromNbtElement(BySides.serializer(ChessPlayerInfoSerializer), get("players")!!)
    val preset: String = config.decodeFromNbtElement(get("preset")!!)
    val variant: ChessVariant = config.decodeFromNbtElement(ChessVariant.serializer(), get("variant")!!)
    val simpleCastling: Boolean = config.decodeFromNbtElement(get("simpleCastling")!!)
    val components = config.decodeFromNbtElement(ListSerializer(ComponentDataSerializer), get("components")!!)
    return ChessGame(GameSettings(preset, simpleCastling, variant, components), players, uuid).start()
}