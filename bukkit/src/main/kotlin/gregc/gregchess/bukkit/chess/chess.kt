package gregc.gregchess.bukkit.chess

import gregc.gregchess.*
import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.chess.component.arena
import gregc.gregchess.bukkit.chess.component.spectators
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.componentKey
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.inventory.ItemStack

val Color.configName get() = name.snakeToPascal()

fun PieceType.getItem(color: Color): ItemStack {
    val item = ItemStack(itemMaterial[color])
    val meta = item.itemMeta!!
    meta.setDisplayName(config.getPathString("Chess.Color.${color.configName}.Piece", localName))
    item.itemMeta = meta
    return item
}

val PieceType.configName get() = name.snakeToPascal()
val PieceType.section get() = module.bukkit.config.getConfigurationSection("Chess.Piece.$configName")!!
val PieceType.localChar get() = section.getString("Char")!!.single()
val PieceType.localName get() = section.getPathString("Name")
fun PieceType.getSound(s: String) = Sound.valueOf(section.getString("Sound.$s")!!)
val PieceType.itemMaterial get() = byColor { Material.valueOf(section.getString("Item.${it.configName}")!!) }
val PieceType.structure
    get() = byColor { section.getStringList("Structure.${it.configName}").map { m -> Material.valueOf(m) } }

val Piece.item get() = type.getItem(color)

@Suppress("UNCHECKED_CAST")
val <T : Any> MoveNameToken<T>.localName
    get() = (type.module[BukkitRegistryTypes.MOVE_NAME_TOKEN_STRING][type] as MoveNameTokenInterpreter<T>)(value)
val MoveName.localName get() = joinToString("") { it.token.localName }

val Floor.material get() = Material.valueOf(config.getString("Chess.Floor.${name.snakeToPascal()}")!!)

fun BoardPiece.getInfo(game: ChessGame) = buildTextComponent {
    append("Type: $color $type\n")
    append("Position: $pos\n")
    append(if (hasMoved) "Has moved\n" else "Has not moved\n")
    appendCopy("Game: ${game.uuid}\n", game.uuid)
    val moves = getLegalMoves(game.board)
    append("All moves: ${moves.joinToString { it.name.localName }}")
    moves.groupBy { m -> game.variant.getLegality(m, game) }.forEach { (l, m) ->
        append("\n${l.prettyName}: ${m.joinToString { it.name.localName }}")
    }
}

fun ChessGame.getInfo() = buildTextComponent {
    appendCopy("UUID: $uuid\n", uuid)
    append("Players: ${players.toList().joinToString { "${it.name} as ${it.color.configName}" }}\n")
    append("Spectators: ${spectators.spectators.joinToString { it.name }}\n")
    append("Arena: ${arena.name}\n")
    append("Preset: ${settings.name}\n")
    append("Variant: ${variant.key}\n")
    append("Components: ${components.joinToString { it::class.componentKey.toString() }}")
}

val GameResults.name
    get() = endReason.module.bukkit.config
        .getPathString("Chess.EndReason.${endReason.name.snakeToPascal()}", *args.toTypedArray())

val GameResults.message
    get() = score.let {
        when (it) {
            is GameScore.Draw -> config.getPathString("Message.GameFinished.ItWasADraw", name)
            is GameScore.Victory -> config.getPathString("Message.GameFinished." + it.winner.configName + "Won", name)
        }
    }

val PropertyType.localName get() = module.bukkit.config.getPathString("Scoreboard.${name.snakeToPascal()}")