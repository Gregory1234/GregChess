package gregc.gregchess.bukkit.chess

import gregc.gregchess.*
import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.chess.component.BukkitRenderer
import gregc.gregchess.bukkit.chess.component.spectators
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.*
import gregc.gregchess.chess.variant.ChessVariant
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import org.bukkit.*
import org.bukkit.inventory.ItemStack
import java.util.*
import kotlin.reflect.KClass

val Side.configName get() = name.snakeToPascal()

fun PieceType.getItem(side: Side): ItemStack {
    val item = ItemStack(itemMaterial[side])
    val meta = item.itemMeta!!
    meta.setDisplayName(config.getPathString("Chess.Side.${side.configName}.Piece", localName))
    item.itemMeta = meta
    return item
}

val PieceType.configName get() = name.snakeToPascal()
val PieceType.section get() = module.bukkit.config.getConfigurationSection("Chess.Piece.$configName")!!
val PieceType.localChar get() = section.getString("Char")!!.single()
val PieceType.localName get() = section.getPathString("Name")
fun PieceType.getSound(s: String) = Sound.valueOf(section.getString("Sound.$s")!!)
val PieceType.itemMaterial get() = bySides { Material.valueOf(section.getString("Item.${it.configName}")!!) }
val PieceType.structure
    get() = bySides { section.getStringList("Structure.${it.configName}").map { m -> Material.valueOf(m) } }

val Piece.item get() = type.getItem(side)

@Suppress("UNCHECKED_CAST")
val <T> MoveNameToken<T>.localName
    get() = (type.module[BukkitRegistryTypes.MOVE_NAME_TOKEN_STRING][type] as MoveNameTokenInterpreter<T>)(value)
val MoveName.localName get() = joinToString("") { it.localName }

val Floor.material get() = Material.valueOf(config.getString("Chess.Floor.${name.snakeToPascal()}")!!)

fun BoardPiece.getInfo() = buildTextComponent {
    append("Type: $side $type\n")
    appendCopy("UUID: $uuid\n", uuid)
    append("Position: $pos\n")
    append(if (hasMoved) "Has moved\n" else "Has not moved\n")
    val game = square.game
    appendCopy("Game: ${game.uuid}\n", game.uuid)
    val moves = square.bakedMoves.orEmpty()
    append("All moves: ${moves.joinToString { it.baseName().localName }}")
    moves.groupBy { m -> game.variant.getLegality(m) }.forEach { (l, m) ->
        append("\n${l.prettyName}: ${m.joinToString { it.baseName().localName }}")
    }
}

val KClass<out Component>.componentId get() = componentModule.namespace + ":" + componentName
@get:JvmName("getComponentDataId")
val KClass<out ComponentData<*>>.componentId get() = componentModule.namespace + ":" + componentName

val ChessVariant.id get() = module.namespace + ":" + name

fun ChessGame.getInfo() = buildTextComponent {
    appendCopy("UUID: $uuid\n", uuid)
    append("Players: ${players.toList().joinToString { "${it.name} as ${it.side.configName}" }}\n")
    append("Spectators: ${spectators.spectators.joinToString { it.name }}\n")
    append("Arena: ${arena.name}\n")
    append("Preset: ${settings.name}\n")
    append("Variant: ${variant.id}\n")
    append("Components: ${components.joinToString { it::class.componentId }}")
}

@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
@Suppress("UNCHECKED_CAST")
private fun <T: ComponentData<*>> Json.serializeComponent(v: T) = buildJsonObject {
    put("type", encodeToJsonElement(v::class.componentId))
    if(v::class.objectInstance == null)
        put("data", encodeToJsonElement(v::class.serializer() as KSerializer<T>, v))
}

fun ChessGame.serializeToJson(config: Json = Json): String = config.encodeToString(JsonObject(mapOf(
    "uuid" to config.encodeToJsonElement(uuid.toString()),
    "white" to config.encodeToJsonElement((players[white] as BukkitPlayer).player.uniqueId.toString()),
    "black" to config.encodeToJsonElement((players[black] as BukkitPlayer).player.uniqueId.toString()),
    "preset" to config.encodeToJsonElement(settings.name),
    "variant" to config.encodeToJsonElement(settings.variant),
    "simpleCastling" to config.encodeToJsonElement(settings.simpleCastling),
    "components" to JsonArray(components.map { config.serializeComponent(it.data) })
)))

@OptIn(InternalSerializationApi::class)
fun Json.deserializeComponent(s: JsonElement): ComponentData<*> {
    val type: String = decodeFromJsonElement(s.jsonObject["type"]!!)
    val cl = ChessModule[type.substringBefore(":")][RegistryType.COMPONENT_CLASS][type.substringAfter(":")].componentDataClass
    return cl.objectInstance ?: decodeFromJsonElement(cl.serializer(), s.jsonObject["data"]!!)
}

fun String.recreateGameFromJson(config: Json = Json): ChessGame = config.decodeFromString<JsonObject>(this).run {
    val uuid = UUID.fromString(config.decodeFromJsonElement(get("uuid")!!))
    val white = Bukkit.getPlayer(UUID.fromString(config.decodeFromJsonElement(get("white")!!)))!!
    val black = Bukkit.getPlayer(UUID.fromString(config.decodeFromJsonElement(get("black")!!)))!!
    val preset: String = config.decodeFromJsonElement(get("preset")!!)
    val variant: ChessVariant = config.decodeFromJsonElement(get("variant")!!)
    val simpleCastling: Boolean = config.decodeFromJsonElement(get("simpleCastling")!!)
    val components = get("components")!!.jsonArray.map { config.deserializeComponent(it) }
    ChessGame(GameSettings(preset, simpleCastling, variant, components), uuid).addPlayers {
        bukkit(white, Side.WHITE, white == black)
        bukkit(black, Side.BLACK, white == black)
    }.start()
}

val EndReason<*>.configName get() = name.snakeToPascal()
val GameResults.name
    get() = endReason.module.bukkit.config.getPathString("Chess.EndReason.${endReason.configName}", *args.toTypedArray())

val GameResults.message
    get() = score.let {
        when (it) {
            is GameScore.Draw -> config.getPathString("Message.GameFinished.ItWasADraw", name)
            is GameScore.Victory -> config.getPathString("Message.GameFinished." + it.winner.configName + "Won", name)
        }
    }

val PropertyType.localName get() = module.bukkit.config.getPathString("Scoreboard.${name.snakeToPascal()}")

val ChessGame.renderer get() = requireComponent<BukkitRenderer>()