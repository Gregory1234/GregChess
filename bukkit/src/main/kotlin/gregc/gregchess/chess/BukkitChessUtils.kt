package gregc.gregchess.chess

import gregc.gregchess.*
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.inventory.ItemStack

interface PieceTypeBukkitConfig : PieceTypeConfig {
    val item: BySides<Material>
    val structure: BySides<List<Material>>
    fun sound(s: PieceSound): Sound
}

interface BukkitChessConfig : ChessConfig {
    fun getFloor(f: Floor): Material
    fun getBukkitPieceType(p: PieceType): PieceTypeBukkitConfig
}

val Config.bukkitChess: BukkitChessConfig by Config

interface StockfishConfig : ConfigBlock {
    val hasStockfish: Boolean
    val stockfishCommand: String
    val engineName: String
}

val Config.stockfish: StockfishConfig by Config

fun PieceType.getItem(side: Side) = Localized { lang ->
    val item = ItemStack(itemMaterial[side])
    val meta = item.itemMeta!!
    meta.setDisplayName(side.getPieceName(pieceName.get(lang)))
    item.itemMeta = meta
    item
}

val PieceType.bukkitConfig get() = Config.bukkitChess.getBukkitPieceType(this)

fun PieceType.getSound(s: PieceSound) = bukkitConfig.sound(s)
val PieceType.itemMaterial get() = bukkitConfig.item
val PieceType.structure get() = bukkitConfig.structure

val Piece.item get() = type.getItem(side)

val Floor.material get() = Config.bukkitChess.getFloor(this)

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

val EndReasonConfig.pluginRestart by EndReasonConfig
val EndReasonConfig.arenaRemoved by EndReasonConfig