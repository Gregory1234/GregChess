package gregc.gregchess.bukkit.piece

import gregc.gregchess.bukkit.config
import gregc.gregchess.bukkit.configName
import gregc.gregchess.bukkit.match.uuid
import gregc.gregchess.bukkit.move.localMoveFormatter
import gregc.gregchess.bukkitutils.getPathString
import gregc.gregchess.bukkitutils.textComponent
import gregc.gregchess.match.ChessMatch
import gregc.gregchess.piece.*
import gregc.gregchess.registry.module
import gregc.gregchess.registry.name
import gregc.gregchess.utils.snakeToPascal
import gregc.gregchess.variant.ChessVariant

val PieceType.configName get() = name.snakeToPascal()
val PieceType.section get() = module.config.getConfigurationSection("Chess.Piece.$configName")!!
val PieceType.localChar get() = section.getString("Char")!!.single()
val PieceType.localName get() = section.getPathString("Name")

val Piece.localChar
    get() = type.localChar
val Piece.localName
    get() = config.getPathString("Chess.Color.${color.configName}.Piece", type.localName)

fun BoardPiece.getInfo(match: ChessMatch) = textComponent {
    text("Type: $color $type\n")
    text("Position: $pos\n")
    text(if (hasMoved) "Has moved\n" else "Has not moved\n")
    text("Match: ${match.uuid}\n") {
        onClickCopy(match.uuid)
    }
    val moves = getMoves(match.board)
    text("All moves: ${moves.joinToString { match.variant.localMoveFormatter.format(it) }}")
    moves.groupBy { m -> match.variant.getLegality(m, match.board) }.forEach { (l, m) ->
        if (l == ChessVariant.MoveLegality.LEGAL)
            match.resolveName(*m.toTypedArray())
        text("\n${l.prettyName}: ${m.joinToString { match.variant.localMoveFormatter.format(it) }}")
    }
}