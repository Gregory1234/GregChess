@file:OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
@file:Suppress("UNCHECKED_CAST")

package gregc.gregchess.fabric.chess

import drawer.nbt.NbtFormat
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.ComponentDataSerializer
import gregc.gregchess.chess.variant.ChessVariant
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
}


// TODO: remove the repetition

fun ChessGame.serializeToNbt(config: NbtFormat): NbtCompound = NbtCompound().apply {
    putUuid("uuid", uuid)
    put("players", config.serialize(BySides.serializer(ChessPlayerInfoSerializer), bySides { players[it].info }))
    putString("preset", settings.name)
    putString("variant", variant.key.toString())
    putBoolean("simpleCastling", settings.simpleCastling)
    put("components", config.serialize(ListSerializer(ComponentDataSerializer), components.map { it.data }))
}

fun NbtCompound.recreateGameFromNbt(config: NbtFormat): ChessGame {
    val uuid = getUuid("uuid")
    val players: BySides<ChessPlayerInfo> = config.deserialize(BySides.serializer(ChessPlayerInfoSerializer), get("players")!!)
    val preset: String = getString("preset")
    val variant: ChessVariant = config.deserialize(ChessVariant.serializer(), get("variant")!!)
    val simpleCastling: Boolean = getBoolean("simpleCastling")
    val components = config.deserialize(ListSerializer(ComponentDataSerializer), get("components")!!)
    return ChessGame(GameSettings(preset, simpleCastling, variant, components), players, uuid).start()
}