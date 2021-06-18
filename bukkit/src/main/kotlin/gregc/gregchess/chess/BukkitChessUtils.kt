package gregc.gregchess.chess

import gregc.gregchess.*
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.inventory.ItemStack

interface BukkitPieceConfig : PieceConfig {
    fun getPieceItem(t: PieceType): BySides<Material>
    fun getPieceStructure(t: PieceType): BySides<List<Material>>
    fun getPieceSound(t: PieceType, s: PieceSound): Sound
}

val Config.bukkitPiece: BukkitPieceConfig by Config


interface BukkitChessConfig : ChessConfig {
    fun getFloor(f: Floor): Material
}

val Config.bukkitChess: BukkitChessConfig by Config

interface StockfishConfig : ConfigBlock {
    val hasStockfish: Boolean
    val stockfishCommand: String
    val engineName: String
}

val Config.stockfish: StockfishConfig by Config

fun PieceType.getItem(side: Side): ItemStack {
    val item = ItemStack(itemMaterial[side])
    val meta = item.itemMeta!!
    meta.setDisplayName(side.getPieceName(pieceName))
    item.itemMeta = meta
    return item
}

fun PieceType.getSound(s: PieceSound) = Config.bukkitPiece.getPieceSound(this, s)
val PieceType.itemMaterial get() = Config.bukkitPiece.getPieceItem(this)
val PieceType.structure get() = Config.bukkitPiece.getPieceStructure(this)

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