package gregc.gregchess.chess

import gregc.gregchess.*
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.inventory.ItemStack

interface StockfishConfig : ConfigBlock {
    val hasStockfish: Boolean
    val stockfishCommand: String
    val engineName: String
}

val Config.stockfish: StockfishConfig by Config

fun PieceType.getItem(side: Side) = Localized { lang ->
    val item = ItemStack(itemMaterial[side])
    val meta = item.itemMeta!!
    meta.setDisplayName(config.getLocalizedString("Chess.Side.${side.standardName}.Piece", pieceName).get(lang))
    item.itemMeta = meta
    item
}

val PieceType.view get() = config["Chess.Piece.$standardName"]
val PieceType.pieceName get() = view.getLocalizedString("Name")
fun PieceType.getSound(s: PieceSound): Sound = view.getEnum("Sound.${s.standardName}", Sound.BLOCK_STONE_HIT)
val PieceType.itemMaterial get() = BySides { view.getEnum("Item.${it.standardName}", Material.AIR) }
val PieceType.structure get() = BySides { view.getEnumList<Material>("Structure.${it.standardName}") }

val Piece.item get() = type.getItem(side)

val Floor.material get() = config.getEnum<Material>("Chess.Floor.${standardName}", Material.AIR)

fun BoardPiece.getInfo() = buildTextComponent {
    append("Name: $standardName\n")
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
    append("Spectators: ${spectators.joinToString { it.name }}\n")
    append("Arena: ${arena.name}\n")
    append("Preset: ${settings.name}\n")
    append("Variant: ${variant.name}\n")
    append("Components: ${getComponents().joinToString { it.javaClass.simpleName }}")
}

val EndReason.name
    get() = config.getLocalizedString("Chess.EndReason.$standardName")

val EndReason.message
    get() = config.getLocalizedString(
        "Message.GameFinished." + (winner?.standardName?.plus("Won") ?: "ItWasADraw"), name)