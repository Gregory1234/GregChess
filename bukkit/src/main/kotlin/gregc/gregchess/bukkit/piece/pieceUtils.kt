package gregc.gregchess.bukkit.piece

import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkitutils.*
import gregc.gregchess.game.ChessGame
import gregc.gregchess.piece.*
import gregc.gregchess.registry.module
import gregc.gregchess.registry.name
import gregc.gregchess.snakeToPascal
import gregc.gregchess.variant.ChessVariant
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.inventory.ItemStack

val PieceType.configName get() = name.snakeToPascal()
val PieceType.section get() = module.config.getConfigurationSection("Chess.Piece.$configName")!!
val PieceType.localChar get() = section.getString("Char")!!.single()
val PieceType.localName get() = section.getPathString("Name")
fun PieceType.getSound(s: String) = Sound.valueOf(section.getString("Sound.$s")!!)

val Piece.localChar
    get() = type.localChar
val Piece.localName
    get() = config.getPathString("Chess.Color.${color.configName}.Piece", type.localName)
val Piece.structure
    get() = type.section.getStringList("Structure.${color.configName}").map { m -> Material.valueOf(m) }
val Piece.item: ItemStack
    get() = itemStack(Material.valueOf(type.section.getString("Item.${color.configName}")!!)) {
        meta { name = localName }
    }

fun BoardPiece.getInfo(game: ChessGame) = textComponent {
    text("Type: $color $type\n")
    text("Position: $pos\n")
    text(if (hasMoved) "Has moved\n" else "Has not moved\n")
    text("Game: ${game.uuid}\n") {
        onClickCopy(game.uuid)
    }
    val moves = getMoves(game.board)
    text("All moves: ${moves.joinToString { game.variant.localMoveFormatter.format(it) }}")
    moves.groupBy { m -> game.variant.getLegality(m, game.board) }.forEach { (l, m) ->
        if (l == ChessVariant.MoveLegality.LEGAL)
            game.resolveName(*m.toTypedArray())
        text("\n${l.prettyName}: ${m.joinToString { game.variant.localMoveFormatter.format(it) }}")
    }
}