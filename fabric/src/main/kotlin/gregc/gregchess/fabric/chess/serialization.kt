@file:OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
@file:Suppress("UNCHECKED_CAST")

package gregc.gregchess.fabric.chess

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.ComponentDataSerializer
import gregc.gregchess.chess.variant.ChessVariant
import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.SerializersModule
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
}


// TODO: remove the repetition

fun ChessGame.serializeToJson(config: Json): String = config.encodeToString(JsonObject(mapOf(
    "uuid" to config.encodeToJsonElement(uuid.toString()),
    "players" to config.encodeToJsonElement(BySides.serializer(ChessPlayerInfoSerializer), bySides { players[it].info }),
    "preset" to config.encodeToJsonElement(settings.name),
    "variant" to config.encodeToJsonElement(settings.variant),
    "simpleCastling" to config.encodeToJsonElement(settings.simpleCastling),
    "components" to config.encodeToJsonElement(ListSerializer(ComponentDataSerializer), components.map { it.data })
)))

fun String.recreateGameFromJson(config: Json): ChessGame = config.decodeFromString<JsonObject>(this).run {
    val uuid = UUID.fromString(config.decodeFromJsonElement(get("uuid")!!))
    val players: BySides<ChessPlayerInfo> = config.decodeFromJsonElement(BySides.serializer(ChessPlayerInfoSerializer), get("players")!!)
    val preset: String = config.decodeFromJsonElement(get("preset")!!)
    val variant: ChessVariant = config.decodeFromJsonElement(get("variant")!!)
    val simpleCastling: Boolean = config.decodeFromJsonElement(get("simpleCastling")!!)
    val components = config.decodeFromJsonElement(ListSerializer(ComponentDataSerializer), get("components")!!)
    ChessGame(GameSettings(preset, simpleCastling, variant, components), players, uuid).start()
}