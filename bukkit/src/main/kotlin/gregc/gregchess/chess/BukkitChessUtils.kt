package gregc.gregchess.chess

import gregc.gregchess.*
import gregc.gregchess.chess.component.BukkitRenderer
import gregc.gregchess.chess.component.spectators
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.inventory.ItemStack


val Side.standardName get() = name.snakeToPascal()

fun PieceType.getItem(side: Side, lang: String): ItemStack {
    val item = ItemStack(itemMaterial[side])
    val meta = item.itemMeta!!
    meta.setDisplayName(config.getLocalizedString("Chess.Side.${side.standardName}.Piece", pieceName).get(lang).chatColor())
    item.itemMeta = meta
    return item
}

val PieceType.view get() = config.getConfigurationSection("Chess.Piece.${id.path.snakeToPascal()}")!!
val PieceType.pieceName get() = view.getLocalizedString("Name")
fun PieceType.getSound(s: String) = Sound.valueOf(view.getString("Sound.$s")!!)
val PieceType.itemMaterial get() = BySides { Material.valueOf(view.getString("Item.${it.standardName}")!!) }
val PieceType.structure get() = BySides { view.getStringList("Structure.${it.standardName}").map { m -> Material.valueOf(m) } }

fun Piece.getItem(lang: String) = type.getItem(side, lang)

val Floor.material get() = Material.valueOf(config.getString("Chess.Floor.${standardName}")!!)

fun BoardPiece.getInfo() = buildTextComponent {
    append("Id: $id\n")
    appendCopy("UUID: $uniqueId\n", uniqueId)
    append("Position: $pos\n")
    append(if (hasMoved) "Has moved\n" else "Has not moved\n")
    val game = square.game
    appendCopy("Game: ${game.uniqueId}\n", game.uniqueId)
    val moves = square.bakedMoves.orEmpty()
    append("All moves: ${moves.joinToString { it.baseStandardName() }}")
    moves.groupBy { m -> game.variant.getLegality(m) }.forEach { (l, m) ->
        append("\n${l.prettyName}: ${m.joinToString { it.baseStandardName() }}")
    }
}

fun ChessGame.getInfo() = buildTextComponent {
    appendCopy("UUID: $uniqueId\n", uniqueId)
    append("Players: ${players.toList().joinToString { "${it.name} as ${it.side.standardName}" }}\n")
    append("Spectators: ${spectators.spectators.joinToString { it.name }}\n")
    append("Arena: ${arena.name}\n")
    append("Preset: ${settings.name}\n")
    append("Variant: ${variant.name}\n")
    append("Components: ${components.joinToString { it.javaClass.simpleName }}")
}

val GameEnd<*>.name
    get() = config.getLocalizedString("Chess.EndReason.${reason.id.path.snakeToPascal()}", *args.toTypedArray())

val GameEnd<*>.message
    get() = result.let {
        when(it) {
            is EndResults.Draw -> config.getLocalizedString("Message.GameFinished.ItWasADraw", name)
            is EndResults.Victory -> config.getLocalizedString("Message.GameFinished." + it.winner.standardName + "Won", name)
        }
    }

val ChessGame.renderer get() = requireComponent<BukkitRenderer>()