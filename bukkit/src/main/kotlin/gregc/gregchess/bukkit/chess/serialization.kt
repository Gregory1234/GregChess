@file:OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
@file:Suppress("UNCHECKED_CAST")

package gregc.gregchess.bukkit.chess

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.ComponentDataSerializer
import gregc.gregchess.chess.variant.ChessVariant
import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.*
import java.util.*

private val playerTypes = mapOf("bukkit" to BukkitPlayerInfo::class, "stockfish" to Stockfish::class)

private fun <T : ChessPlayerInfo> Json.serializePlayer(v: T) = buildJsonObject {
    put("type", playerTypes.filter { it.value == v::class }.keys.first())
    put("value", encodeToJsonElement(v::class.serializer() as KSerializer<T>, v))
}

fun ChessGame.serializeToJson(config: Json = Json): String = config.encodeToString(JsonObject(mapOf(
    "uuid" to config.encodeToJsonElement(uuid.toString()),
    "players" to config.encodeToJsonElement(bySides { config.serializePlayer(players[it].info) }),
    "preset" to config.encodeToJsonElement(settings.name),
    "variant" to config.encodeToJsonElement(settings.variant),
    "simpleCastling" to config.encodeToJsonElement(settings.simpleCastling),
    "components" to config.encodeToJsonElement(ListSerializer(ComponentDataSerializer), components.map { it.data })
)))

private fun Json.deserializePlayer(s: JsonElement): ChessPlayerInfo {
    val cl = playerTypes[decodeFromJsonElement(s.jsonObject["type"]!!)]!!
    return decodeFromJsonElement(cl.serializer(), s.jsonObject["value"]!!)
}

fun String.recreateGameFromJson(config: Json = Json): ChessGame = config.decodeFromString<JsonObject>(this).run {
    val uuid = UUID.fromString(config.decodeFromJsonElement(get("uuid")!!))
    val playersRaw = config.decodeFromJsonElement<BySides<JsonObject>>(get("players")!!)
    val players = bySides { config.deserializePlayer(playersRaw[it]) }
    val preset: String = config.decodeFromJsonElement(get("preset")!!)
    val variant: ChessVariant = config.decodeFromJsonElement(get("variant")!!)
    val simpleCastling: Boolean = config.decodeFromJsonElement(get("simpleCastling")!!)
    val components = config.decodeFromJsonElement(ListSerializer(ComponentDataSerializer), get("components")!!)
    ChessGame(GameSettings(preset, simpleCastling, variant, components), players, uuid).start()
}