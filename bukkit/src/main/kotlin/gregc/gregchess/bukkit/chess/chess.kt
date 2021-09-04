package gregc.gregchess.bukkit.chess

import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.chess.component.BukkitRenderer
import gregc.gregchess.bukkit.chess.component.spectators
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.*
import gregc.gregchess.chess.variant.ChessVariant
import gregc.gregchess.snakeToPascal
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.inventory.ItemStack
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
val <T: Any> MoveNameToken<T>.localName
    get() = (type.module[BukkitRegistryTypes.MOVE_NAME_TOKEN_STRING][type] as MoveNameTokenInterpreter<T>)(value)
val MoveName.localName get() = tokens.joinToString("") { it.localName }

val Floor.material get() = Material.valueOf(config.getString("Chess.Floor.${name.snakeToPascal()}")!!)

fun PieceInfo.getInfo(game: ChessGame) = buildTextComponent {
    append("Type: $side $type\n")
    append("Position: $pos\n")
    append(if (hasMoved) "Has moved\n" else "Has not moved\n")
    appendCopy("Game: ${game.uuid}\n", game.uuid)
    val moves = getLegalMoves(game.board)
    append("All moves: ${moves.joinToString { it.name.localName }}")
    moves.groupBy { m -> game.variant.getLegality(m, game) }.forEach { (l, m) ->
        append("\n${l.prettyName}: ${m.joinToString { it.name.localName }}")
    }
}

val KClass<out Component>.componentId get() = componentModule.namespace + ":" + componentName

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