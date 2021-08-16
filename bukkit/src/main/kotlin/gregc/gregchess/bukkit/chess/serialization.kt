@file:OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
@file:Suppress("UNCHECKED_CAST")

package gregc.gregchess.bukkit.chess

import gregc.gregchess.ChessModule
import gregc.gregchess.RegistryType
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.ComponentData
import gregc.gregchess.chess.component.componentDataClass
import gregc.gregchess.chess.variant.ChessVariant
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.util.*

private fun <T : ComponentData<*>> Json.serializeComponent(v: T) = buildJsonObject {
    put("type", encodeToJsonElement(v::class.componentId))
    if (v::class.objectInstance == null) {
        for ((k,x) in encodeToJsonElement(v::class.serializer() as KSerializer<T>, v).jsonObject)
            put(k, x)
    }
}

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
    "components" to JsonArray(components.map { config.serializeComponent(it.data) })
)))

private fun Json.deserializeComponent(s: JsonElement): ComponentData<*> {
    val type: String = decodeFromJsonElement(s.jsonObject["type"]!!)
    val (namespace, name) = type.split(":")
    val cl = ChessModule[namespace][RegistryType.COMPONENT_CLASS][name].componentDataClass
    return cl.objectInstance ?: decodeFromJsonElement(cl.serializer(), JsonObject(s.jsonObject.filter { it.key != "type"}))
}

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
    val components = get("components")!!.jsonArray.map { config.deserializeComponent(it) }
    ChessGame(GameSettings(preset, simpleCastling, variant, components), players, uuid).start()
}