@file:OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
@file:Suppress("UNCHECKED_CAST")

package gregc.gregchess.bukkit.chess

import gregc.gregchess.bukkit.UUIDAsStringSerializer
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.ComponentDataSerializer
import gregc.gregchess.chess.variant.ChessVariant
import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.SerializersModule
import java.util.*

fun defaultModule() = SerializersModule {
    contextual(UUID::class, UUIDAsStringSerializer)
}

fun ChessGame.serializeToJson(config: Json = Json): String = config.encodeToString(JsonObject(mapOf(
    "uuid" to config.encodeToJsonElement(config.serializersModule.getContextual(UUID::class)!!, uuid),
    "players" to config.encodeToJsonElement(BySides.serializer(ChessPlayerInfoSerializer), bySides { players[it].info }),
    "preset" to config.encodeToJsonElement(settings.name),
    "variant" to config.encodeToJsonElement(variant),
    "simpleCastling" to config.encodeToJsonElement(settings.simpleCastling),
    "components" to config.encodeToJsonElement(ListSerializer(ComponentDataSerializer), components.map { it.data })
)))

fun String.recreateGameFromJson(config: Json = Json): ChessGame = config.decodeFromString<JsonObject>(this).run {
    val uuid = config.decodeFromJsonElement(config.serializersModule.getContextual(UUID::class)!!, get("uuid")!!)
    val players: BySides<ChessPlayerInfo> = config.decodeFromJsonElement(BySides.serializer(ChessPlayerInfoSerializer), get("players")!!)
    val preset: String = config.decodeFromJsonElement(get("preset")!!)
    val variant: ChessVariant = config.decodeFromJsonElement(get("variant")!!)
    val simpleCastling: Boolean = config.decodeFromJsonElement(get("simpleCastling")!!)
    val components = config.decodeFromJsonElement(ListSerializer(ComponentDataSerializer), get("components")!!)
    ChessGame(GameSettings(preset, simpleCastling, variant, components), players, uuid).start()
}